package com.vaadin.kubernetes.starter.sessiontracker.serialization.debug;

import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.StringJoiner;
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
                String bestCandidates = serializedLambdaMap.values().stream()
                        .filter(serializedLambda -> serializedLambda
                                .getFunctionalInterfaceClass()
                                .equals(targetType.replace('.', '/')))
                        .map(serializedLambda -> "\t" + serializedLambda
                                + System.lineSeparator()
                                + tracked.get(serializedLambda).stackInfo)
                        .collect(Collectors.joining(System.lineSeparator()));

                if (!bestCandidates.isEmpty()) {
                    log(CATEGORY_ERRORS,
                            "SERIALIZED LAMBDA CLASS CAST EXCEPTION BEST CANDIDATES:"
                                    + System.lineSeparator()
                                    + "======================================================="
                                    + System.lineSeparator() + bestCandidates);
                }
            }
        }
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(DebugMode.class);
    }

    private final Map<Integer, SerializedLambda> serializedLambdaMap = new HashMap<>();

    void pushDeserialization(Class<?> type, Track track, Object obj) {
        if (obj instanceof SerializedLambda) {
            // Detected a potential self reference class cast exception
            // get serialized instance from tracked objects
            // save it and then ensure it is deserialized correctly
            int trackId = track.id;
            Object serializedObject = tracked.values().stream()
                    .filter(t -> t.id == trackId).findFirst().get().object;
            // Following condition should always be true, otherwise it means the
            // stream is somehow corrupted
            if (serializedObject instanceof SerializedLambda) {
                SerializedLambda cast = (SerializedLambda) serializedObject;
                MethodHandleInfo
                        .referenceKindToString(cast.getImplMethodKind());
                serializedLambdaMap.put(trackId, cast);
            } else {
                getLogger().warn(
                        "Expected tracked object {} to be instance of {}, but was {} ",
                        trackId, SerializedLambda.class.getName(),
                        (serializedObject == null) ? "NULL"
                                : serializedObject.getClass());
            }
        } else if (type == SerializedLambda.class) {
            // Reflection not enabled, add all potential candidates
            getLogger().trace("Reflection not enabled, can't get SerializedLambda details");
        }
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

            // Analyzing class for a new object: readNonProxyDesc
            /*
             * if (type != null && track.id == -1) { testStack.push(new
             * ObjectStreamClassEntry( ObjectStreamClass.lookup(type),
             * Track.unknown(track.depth, type))); }
             */
        }
        if (track != null && !(obj instanceof ObjectStreamClass)) {
            deserializationStack.push(track);
        }
        if (obj instanceof ObjectStreamClass) {
            ObjectStreamClass cast = (ObjectStreamClass) obj;
            LoggerFactory.getLogger(Job.class)
                    .info("Start serialization of {} with fields [{}]",
                            cast.getName(),
                            Stream.of(cast.getFields())
                                    .map(ObjectStreamField::getName)
                                    .collect(Collectors.joining(",")));
            testStack.push(new ObjectStreamClassEntry(cast, track));
        }
    }

    private final Stack<ObjectStreamClassEntry> testStack = new Stack<>();

    static class ObjectStreamClassEntry {
        private final ObjectStreamClass desc;

        private final Track track;
        private List<ObjectStreamField> fields;

        ObjectStreamClassEntry(ObjectStreamClass desc, Track track) {
            this.desc = desc;
            this.track = track;
            this.fields = Stream.of(desc.getFields())
                    .filter(f -> !f.isPrimitive())
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        // Visible for test
        List<String> fields() {
            return fields.stream().map(ObjectStreamField::getName)
                    .collect(Collectors.toList());
        }

        boolean canPop(Track track) {
            return track.depth < this.track.depth;
            // return (this.track.id != -1 && track.id == this.track.id
            // && track.depth == this.track.depth)
            // || track.depth <= this.track.depth;
        }

        boolean canPopOld(Track track) {
            if (track.depth > this.track.depth + 1) {
                return false;
            } else if (track.depth == this.track.depth + 1) {
                Iterator<ObjectStreamField> iterator = fields.iterator();
                while (iterator.hasNext()) {
                    ObjectStreamField field = iterator.next();
                    LoggerFactory.getLogger(ObjectStreamClassEntry.class).info(
                            "Is tracked object {} of type {} value for field {}.{} of type {}?",
                            track.id, track.className, field.getType(),
                            field.getName(), desc.getName());
                    try {
                        if (field.getType().isAssignableFrom(
                                Class.forName(track.className))) {
                            LoggerFactory
                                    .getLogger(ObjectStreamClassEntry.class)
                                    .info("Educated guess is yes");
                            iterator.remove();
                            break;
                        }
                    } catch (ClassNotFoundException e) {
                        LoggerFactory.getLogger(ObjectStreamClassEntry.class)
                                .info("Holy Crap! {}", e.getMessage());
                    }
                }
                return fields.isEmpty();
            }
            return true;
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

    void popDeserialization(Track track, Object obj) {
        if (track != null) {
            if (serializedLambdaMap.containsKey(track.id)
                    && !(obj instanceof SerializedLambda)) {
                serializedLambdaMap.remove(track.id);
            }
            while (!testStack.isEmpty() && testStack.peek().canPop(track)) {
                testStack.pop();
            }
            if (!testStack.isEmpty()) {
                testStack.pop();
            }
            lastDeserialized = track;

            while (!deserializationStack.isEmpty()
                    && (track.id == deserializationStack.peek().id
                            || track.depth < deserializationStack
                                    .peek().depth)) {
                deserializationStack.pop();
            }
            /*
            if (deserializationStack.stream().anyMatch(t -> t.id == track.id)) {
                while (!deserializationStack.isEmpty()
                        && deserializationStack.peek().id != track.id) {
                    deserializationStack.pop();
                }
            }
             */
        } else {
            // Tracking disabled
            getLogger().trace("Tracking disable :(");
        }
    }
    // Check that SerializedLambda has been correctly replaced by an
    // functional interface implementation instance

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
