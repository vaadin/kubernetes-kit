/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker.serialization.debug;

import java.io.InvalidObjectException;
import java.io.ObjectStreamClass;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionInfo;

import static com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.Result.CATEGORY_ERRORS;

class Job {

    private static final Pattern SERIALIZEDLAMBDA_CANNOT_ASSIGN = Pattern
            .compile(
                    "cannot assign instance of java.lang.invoke.SerializedLambda to field [^ ]+ of type ([^ ]+) in instance [^ ]+");
    private static final Pattern SERIALIZEDLAMBDA_CANNOT_CAST = Pattern.compile(
            "class java.lang.invoke.SerializedLambda cannot be cast to class ([^ ]+)( |$)");

    private final CountDownLatch serializationCompletedLatch = new CountDownLatch(
            1);
    private final CountDownLatch serializationStartedLatch = new CountDownLatch(
            1);
    private final String sessionId;
    private long startTimeNanos;
    private final Set<Outcome> outcome = new LinkedHashSet<>();
    private final Map<String, List<String>> messages = new LinkedHashMap<>();
    private String clusterKey;
    private final Map<Object, Track> tracked = new IdentityHashMap<>();

    private final ArrayDeque<Track> deserializingStack = new ArrayDeque<>();
    private final Map<String, List<String>> unserializableDetails = new HashMap<>();

    private final Map<Integer, SerializedLambda> serializedLambdaMap = new HashMap<>();

    Job(String sessionId, String clusterKey) {
        this.sessionId = sessionId;
        this.clusterKey = clusterKey;
        this.startTimeNanos = System.nanoTime();
    }

    /**
     * Blocks the thread for up to the defined timeout in milliseconds, waiting
     * for serialization to be completed.
     *
     * @param timeout
     *            the timeout in milliseconds to wait for the serialization to
     *            be completed.
     * @param logger
     *            the logger to add potential error information.
     * @return the serialized session holder.
     */
    boolean waitForSerializationCompletion(int timeout, Logger logger) {
        boolean completed = true;
        try {
            completed = serializationStartedLatch.await(timeout,
                    TimeUnit.MILLISECONDS);
            if (!completed) {
                timeout();
                logger.error(
                        "Session serialization timed out because did not started in {} ms, "
                                + "most likely because another attempt is already in progress.",
                        timeout);
                return false;
            }
        } catch (Exception e) { // NOSONAR
            logger.error("Testing of session serialization failed", e);
        }
        if (completed) {
            try {
                completed = serializationCompletedLatch.await(timeout,
                        TimeUnit.MILLISECONDS);
                if (!completed) {
                    timeout();
                    logger.error(
                            "Session serialization timed out because did not complete in {} ms. "
                                    + "Increase the serialization timeout (in milliseconds) by the "
                                    + "'vaadin.serialization.timeout' application or system property.",
                            timeout);
                    return false;
                }
            } catch (Exception e) { // NOSONAR
                logger.error("Testing of session serialization failed", e);
            }
        }
        return completed;
    }

    void reset() {
        startTimeNanos = System.nanoTime();
        outcome.clear();
        messages.clear();
        tracked.clear();
        deserializingStack.clear();
        unserializableDetails.clear();
        serializedLambdaMap.clear();
        outcome.add(Outcome.SERIALIZATION_FAILED);
    }

    void cancel() {
        outcome.add(Outcome.CANCELED);
        if (serializationStartedLatch.getCount() > 0) {
            serializationStartedLatch.countDown();
        }
        if (serializationCompletedLatch.getCount() > 0) {
            serializationCompletedLatch.countDown();
        }
    }

    public void serializationStarted() {
        serializationStartedLatch.countDown();
        reset();
    }

    void notSerializable(Object obj) {
        Class<?> clazz = obj.getClass();
        Track track = tracked.get(obj);
        List<String> details = unserializableDetails
                .computeIfAbsent(clazz.getName(), unused -> {
                    List<String> info = new ArrayList<>();
                    if (clazz.isSynthetic() && !clazz.isAnonymousClass()
                            && !clazz.isLocalClass()
                            && clazz.getSimpleName().contains("$$Lambda$")
                            && clazz.getInterfaces().length == 1) {
                        // Additional details for lamdba expressions
                        Class<?> samInterface = clazz.getInterfaces()[0];
                        Method samMethod = samInterface.getMethods()[0];
                        StringJoiner sj = new StringJoiner(",",
                                samMethod.getName() + "(", ")");
                        for (Class<?> parameterType : samMethod
                                .getParameterTypes()) {
                            sj.add(parameterType.getTypeName());
                        }
                        info.add(String.format("[ SAM interface: %s.%s ]",
                                samInterface.getName(), sj));
                    }
                    return info;
                });
        if (!track.stackInfo.isEmpty()) {
            details.add(String.format(
                    "Start Track ID: %d, Stack depth: %d. Reference stack: ",
                    track.id, track.depth));
            details.addAll(track.stackInfo);
            details.add(String.format("End Track ID: %d", track.id));
            details.add("");
        }
        logDistinct(Outcome.NOT_SERIALIZABLE_CLASSES.name(), clazz.getName());
        outcome.add(Outcome.NOT_SERIALIZABLE_CLASSES);
    }

    void serialized(SessionInfo info) {
        try {
            if (info != null) {
                if (!clusterKey.equals(info.getClusterKey())) {
                    throw new IllegalStateException(
                            "Unexpected cluster key " + info.getClusterKey()
                                    + " on session info, expecting it to be "
                                    + clusterKey);
                }
                clusterKey = info.getClusterKey();
                outcome.add(Outcome.DESERIALIZATION_FAILED);
                if (!outcome.contains(Outcome.NOT_SERIALIZABLE_CLASSES)) {
                    outcome.remove(Outcome.SERIALIZATION_FAILED);
                }
            }
        } finally {
            serializationCompletedLatch.countDown();
        }
    }

    void serializationFailed(Exception ex) {
        outcome.add(Outcome.SERIALIZATION_FAILED);
        log(CATEGORY_ERRORS,
                Outcome.SERIALIZATION_FAILED.name() + ": " + ex.getMessage());
    }

    void timeout() {
        outcome.remove(Outcome.SERIALIZATION_FAILED);
        outcome.add(Outcome.SERIALIZATION_TIMEOUT);
    }

    void deserialized() {
        outcome.remove(Outcome.DESERIALIZATION_FAILED);
    }

    void deserializationFailed(Exception ex) {
        outcome.add(Outcome.DESERIALIZATION_FAILED);
        log(CATEGORY_ERRORS,
                Outcome.DESERIALIZATION_FAILED.name() + ": " + ex.getMessage());
        Throwable cause = tryUnwrapLambdaDeserializationCause(ex);
        if (cause instanceof ClassCastException
                && cause.getMessage().contains(SerializedLambda.class.getName())
                && !serializedLambdaMap.isEmpty()) {
            String targetType = tryDetectClassCastTarget(cause.getMessage());
            if (targetType != null) {
                String bestCandidates = serializedLambdaMap.values().stream()
                        .filter(serializedLambda -> serializedLambda
                                .getFunctionalInterfaceClass()
                                .equals(targetType.replace('.', '/')))
                        .flatMap(serializedLambda -> Stream.concat(
                                Stream.of("\t" + serializedLambda),
                                tracked.get(serializedLambda).stackInfo
                                        .stream()))
                        .collect(Collectors.joining(System.lineSeparator()));

                if (!bestCandidates.isEmpty()) {
                    log(CATEGORY_ERRORS,
                            "SERIALIZED LAMBDA CLASS CAST EXCEPTION BEST CANDIDATES:"
                                    + System.lineSeparator()
                                    + "======================================================="
                                    + System.lineSeparator() + bestCandidates);
                }
            }
        } else {
            dumpDeserializationStack()
                    .ifPresent(message -> log(CATEGORY_ERRORS, message));
        }
    }

    // Base on the JDK version, SerializedLambda.readResolve may differently
    // wrap the cause of error
    private Throwable tryUnwrapLambdaDeserializationCause(Throwable exception) {
        Throwable cause = exception;
        if (cause instanceof InvalidObjectException) {
            cause = cause.getCause();
        }
        if (cause instanceof InvocationTargetException) {
            cause = cause.getCause();
        }
        return cause;
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(DebugMode.class);
    }

    void pushDeserialization(Track track, Object obj) {
        if (obj instanceof SerializedLambda) {
            // Detected a potential self reference class cast exception
            // get serialized instance from tracked objects
            // save it and then ensure it is deserialized correctly
            int trackId = track.id;
            Object serializedObject = tracked.values().stream()
                    .filter(t -> t.id == trackId).findFirst().map(t -> t.object)
                    .orElse(null);
            // Following condition should always be true, otherwise it means the
            // stream is somehow corrupted
            if (serializedObject instanceof SerializedLambda) {
                SerializedLambda cast = (SerializedLambda) serializedObject;
                serializedLambdaMap.put(trackId, cast);
            } else {
                getLogger().warn(
                        "Expected tracked object {} to be instance of {}, but was {} ",
                        trackId, SerializedLambda.class.getName(),
                        (serializedObject == null) ? "NULL"
                                : serializedObject.getClass());
            }
        }
        if (obj instanceof ObjectStreamClass) {
            getLogger().trace(
                    "Push deserialization stack element Track ID: {}, Handle: {} (estimated: {}), depth: {}, desc; {}",
                    track.id, track.getHandle(), track.id == -1, track.depth,
                    obj);
            deserializingStack.push(track);
        }
    }

    void popDeserialization(Track track, Object obj) {
        if (track != null && serializedLambdaMap.containsKey(track.id)
                && !(obj instanceof SerializedLambda)) {
            serializedLambdaMap.remove(track.id);
        }
        if (!deserializingStack.isEmpty()) {
            Track pop = deserializingStack.pop();
            getLogger().trace(
                    "Pop deserialization stack element Track ID: {}, Handle: {} (estimated: {}), depth: {}",
                    pop.id, pop.getHandle(), pop.id == -1, pop.depth);
        }

    }
    // Check that SerializedLambda has been correctly replaced by an
    // functional interface implementation instance

    Optional<String> dumpDeserializationStack() {
        if (!deserializingStack.isEmpty()) {
            return Optional.of(deserializingStack.peek())
                    .flatMap(stackEntry -> tracked.values().stream().filter(
                            t -> t.getHandle() == stackEntry.getHandle())
                            .findFirst())
                    .map(track -> {
                        StringJoiner joiner = new StringJoiner(
                                System.lineSeparator());
                        joiner.add(
                                "DESERIALIZATION STACK. Process failed at depth "
                                        + track.depth);
                        joiner.add("\t- object (class \"" + track.className
                                + "\")");
                        if (!track.stackInfo.isEmpty()) {
                            track.stackInfo.forEach(joiner::add);
                        }
                        return joiner.toString();
                    });
        }
        return Optional.empty();
    }

    Result complete() {
        if (outcome.isEmpty()) {
            outcome.add(Outcome.SUCCESS);
        }
        long duration = TimeUnit.NANOSECONDS
                .toMillis(System.nanoTime() - this.startTimeNanos);
        if (!outcome.contains(Outcome.SERIALIZATION_TIMEOUT)) {
            messages.computeIfPresent(Outcome.NOT_SERIALIZABLE_CLASSES.name(), (
                    unused,
                    info) -> info.stream().flatMap(className -> Stream.concat(
                            Stream.of(className),
                            unserializableDetails
                                    .getOrDefault(className,
                                            Collections.emptyList())
                                    .stream().map(entry -> "\t" + entry)))
                            .collect(Collectors.toList()));
        }
        return new Result(sessionId, clusterKey, outcome, duration, messages);
    }

    void log(String category, String message) {
        messages.computeIfAbsent(category, unused -> new ArrayList<>())
                .add(message);
    }

    void logDistinct(String category, String message) {
        List<String> list = messages.computeIfAbsent(category,
                unused -> new ArrayList<>());
        if (!list.contains(message)) {
            list.add(message);
        }
    }

    private static String tryDetectClassCastTarget(String message) {
        Matcher matcher = SERIALIZEDLAMBDA_CANNOT_ASSIGN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = SERIALIZEDLAMBDA_CANNOT_CAST.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public void track(Object object, Track track) {
        if (track == null) {
            track = new Track(-1, -1, null, null);
        }
        tracked.put(object, track);
    }
}
