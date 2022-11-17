package com.vaadin.kubernetes.starter.sessiontracker.serialization.debug;

import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.LoggerFactory;

import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionInfo;

import static com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.Result.CATEGORY_ERRORS;

class Job {

    private static final Pattern SERIALIZEDLAMBDA_CANNOT_ASSIGN = Pattern
            .compile(
                    "cannot assign instance of java.lang.invoke.SerializedLambda to field [^ ]+ of type ([^ ]+) in instance [^ ]+");
    private static final Pattern SERIALIZEDLAMBDA_CANNOT_CAST = Pattern.compile(
            "class java.lang.invoke.SerializedLambda cannot be cast to class ([^ ]+)( |$)");

    private final String sessionId;
    private final long startTimeNanos;
    private final Set<Outcome> outcome = new LinkedHashSet<>();
    private final Map<String, List<String>> messages = new LinkedHashMap<>();
    private String storageKey;

    private final Map<Object, Track> tracked = new IdentityHashMap<>();

    private final Deque<Track> deserializationStack = new ArrayDeque<>();
    private Track lastDeserialized = null;
    private final Map<String, List<String>> unserializableDetails = new HashMap<>();

    Job(String sessionId) {
        this.sessionId = sessionId;
        this.startTimeNanos = System.nanoTime();
    }

    public void serializationStarted() {
        // No-Op
        outcome.add(Outcome.SERIALIZATION_FAILED);
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
        if (track.stackInfo != null && !track.stackInfo.isEmpty()) {
            details.add(String.format(
                    "Start Track ID: %d, Stack depth: %d. Reference stack: ",
                    track.id, track.depth));
            details.addAll(
                    track.stackInfo.lines().collect(Collectors.toList()));
            details.add(String.format("End Track ID: %d", track.id));
            details.add("");
        }
        logDistinct(Outcome.NOT_SERIALIZABLE_CLASSES.name(), clazz.getName());
        outcome.add(Outcome.NOT_SERIALIZABLE_CLASSES);
    }

    void serialized(SessionInfo info) {
        if (info != null) {
            storageKey = info.getClusterKey();
            outcome.add(Outcome.DESERIALIZATION_FAILED);
            if (!outcome.contains(Outcome.NOT_SERIALIZABLE_CLASSES)) {
                outcome.remove(Outcome.SERIALIZATION_FAILED);
            }
        }
    }

    void serializationFailed(Exception ex) {
        outcome.add(Outcome.SERIALIZATION_FAILED);
        log(CATEGORY_ERRORS,
                Outcome.SERIALIZATION_FAILED.name() + ": " + ex.getMessage());
    }

    void deserialized() {
        outcome.remove(Outcome.DESERIALIZATION_FAILED);
    }

    void deserializationFailed(Exception ex) {
        outcome.add(Outcome.DESERIALIZATION_FAILED);
        log(CATEGORY_ERRORS,
                Outcome.DESERIALIZATION_FAILED.name() + ": " + ex.getMessage());
        dumpDeserializationStack()
                .ifPresent(message -> log(CATEGORY_ERRORS, message));
        if (ex instanceof ClassCastException
                && ex.getMessage().contains(SerializedLambda.class.getName())
                && messages.containsKey(SerializedLambda.class.getName())) {
            String targetType = tryDetectClassCastTarget(ex.getMessage());
            if (targetType != null) {
                String bestCandidates = messages
                        .getOrDefault(SerializedLambda.class.getName(),
                                Collections.emptyList())
                        .stream()
                        .filter(entry -> entry
                                .contains("functionalInterfaceClass="
                                        + targetType.replace('.', '/')))
                        .map(lambda -> "\t" + lambda)
                        .collect(Collectors.joining(System.lineSeparator()));
                if (!bestCandidates.isEmpty()) {
                    log(CATEGORY_ERRORS,
                            "SERIALIZED LAMBDA CLASS CAST EXCEPTION BEST CANDIDATES:"
                                    + System.lineSeparator()
                                    + "======================================================="
                                    + System.lineSeparator() + bestCandidates);
                }
            }
            log(CATEGORY_ERRORS, messages
                    .getOrDefault(SerializedLambda.class.getName(),
                            Collections.emptyList())
                    .stream().map(lambda -> "\t" + lambda)
                    .collect(Collectors.joining(System.lineSeparator(),
                            "SERIALIZED LAMBDA CLASS CAST EXCEPTION ALL DETECTED TARGETS:"
                                    + System.lineSeparator()
                                    + "============================================================"
                                    + System.lineSeparator(),
                            "")));
        }
    }

    void pushDeserialization(Class<?> type, Track track, Object obj) {
        if (track != null && track.stackInfo == null) {
            int trackId = track.id;
            int trackDepth = track.depth;
            String trackClassName = track.className;

            // Try best match based on depth if track id is provided
            String info = tracked.values().stream()
                    .filter(t -> t.id == trackId || (t.depth == trackDepth
                            && t.className.equals(trackClassName)))
                    .map(t -> t.stackInfo).filter(Objects::nonNull)
                    .collect(Collectors.joining("\n\n"));
            if (info.isEmpty()) {
                // extendedDebugInfo property and/or add-open flag not set
                // add some type info to help debugging issues
                info = guessPotentialStack(type);
            }
            track = new Track(track.id, track.depth, info, null);
        }
        if (track != null) {
            deserializationStack.push(track);
        }
        if (type == ObjectStreamClass.class
                && obj instanceof ObjectStreamClass) {
            ObjectStreamClass cast = (ObjectStreamClass) obj;
            LoggerFactory.getLogger(Job.class)
                    .debug("Start serialization of {} with fields [{}]",
                            cast.getName(),
                            Stream.of(cast.getFields())
                                    .map(ObjectStreamField::getName)
                                    .collect(Collectors.joining(",")));
        }
    }

    private String guessPotentialStack(Class<?> type) {
        String info;
        List<String> entries = new ArrayList<>();
        entries.add(type.getName());
        Arrays.stream(ObjectStreamClass.lookupAny(type).getFields())
                .map(field -> String.format(
                        "\t- field (class \"%s\", name: \"%s\")",
                        field.getType().getName(), field.getName()))
                .collect(Collectors.toCollection(() -> entries));
        if (!deserializationStack.isEmpty()) {
            entries.add(deserializationStack.peek().stackInfo);
        }
        info = entries.stream().map(entry -> "\t" + entry)
                .collect(Collectors.joining(System.lineSeparator()));
        return info;
    }

    void popDeserialization(Track track) {
        lastDeserialized = track;

        if (deserializationStack.stream().anyMatch(t -> t.id == track.id)) {
            while (!deserializationStack.isEmpty()
                    && deserializationStack.peek().id != track.id) {
                deserializationStack.pop();
            }
        }
    }

    Optional<String> dumpDeserializationStack() {
        if (!deserializationStack.isEmpty()) {
            Track track = deserializationStack.peek();
            String builder = "DESERIALIZATION STACK. Process failed at depth "
                    + track.depth + System.lineSeparator() + track.stackInfo;
            return Optional.of(builder);
        }

        if (lastDeserialized != null) {
            String builder = "DESERIALIZATION STACK. Process failed at depth "
                    + lastDeserialized.depth + System.lineSeparator()
                    + lastDeserialized.stackInfo;
            return Optional.of(builder);
        }

        return Optional.empty();
    }

    Result complete() {
        if (outcome.isEmpty()) {
            outcome.add(Outcome.SUCCESS);
        }
        long duration = TimeUnit.NANOSECONDS
                .toMillis(System.nanoTime() - this.startTimeNanos);
        messages.computeIfPresent(Outcome.NOT_SERIALIZABLE_CLASSES.name(),
                (unused, info) -> info.stream()
                        .flatMap(className -> Stream.concat(
                                Stream.of(className),
                                unserializableDetails
                                        .getOrDefault(className,
                                                Collections.emptyList())
                                        .stream().map(entry -> "\t" + entry)))
                        .collect(Collectors.toList()));
        return new Result(sessionId, storageKey, outcome, duration, messages);
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
            track = new Track(-1, -1, "", null);
        }
        tracked.put(object, track);
    }
}
