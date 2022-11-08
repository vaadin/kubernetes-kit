package com.vaadin.azure.starter.sessiontracker.serialization;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.azure.starter.sessiontracker.CurrentKey;
import com.vaadin.azure.starter.sessiontracker.SessionSerializer;
import com.vaadin.azure.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.azure.starter.sessiontracker.backend.SessionInfo;
import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.server.RequestHandler;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;
import com.vaadin.flow.server.startup.ApplicationConfiguration;

/**
 * A {@link RequestHandler} implementation that performs a check on HTTP session
 * serialization and deserialization.
 *
 * The request handler is executed only in development mode and it the
 * {@literal devmode.sessionSerialization.enabled} configuration property is set
 * to {@literal true}.
 *
 * Potential exceptions are caught and logged in the server console.
 *
 * After running serialization and deserialization, detailed information are
 * printed on server logs:
 *
 * <pre>
 * - Process outcomes
 * - List of not serializable classes
 * - Deserialization stack in case of error
 * - Potential causes of SerializedLambda {@link ClassCastException}s
 *   (e.g. self referencing lambdas)
 * </pre>
 *
 * Information are also stored in the form of {@link Result} object as request
 * attribute, under the {@link #SERIALIZATION_TEST_REQUEST_ATTRIBUTE_KEY} key.
 *
 * To get additional information about the serialization process issues set the
 * {@literal -Dsun.io.serialization.extendedDebugInfo} system property to
 * {@literal true}.
 */
public class SerializationDebugRequestHandler implements RequestHandler {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(SerializationDebugRequestHandler.class);

    public static final String SERIALIZATION_TEST_REQUEST_ATTRIBUTE_KEY = SerializationDebugRequestHandler.class
            .getName() + ".RESULT";

    @Override
    public boolean handleRequest(VaadinSession vaadinSession,
            VaadinRequest vaadinRequest, VaadinResponse vaadinResponse) {
        ApplicationConfiguration appConfiguration = ApplicationConfiguration
                .get(vaadinSession.getService().getContext());
        if (appConfiguration.isProductionMode()) {
            LoggerFactory.getLogger(InitListener.class).warn(
                    "SerializationDebugRequestHandler in not enabled in production mode");
            return false;
        }
        if (!appConfiguration.isDevModeSessionSerializationEnabled()) {
            LoggerFactory.getLogger(InitListener.class).warn(
                    "SerializationDebugRequestHandler is enabled only with enable session serialization setting 'vaadin.devmode.sessionSerialization.enabled=true'");
            return false;
        }

        if (HandlerHelper.isRequestType(vaadinRequest,
                HandlerHelper.RequestType.UIDL)) {
            try {
                serializeAndDeserialized(vaadinSession,
                        result -> vaadinRequest.setAttribute(
                                SERIALIZATION_TEST_REQUEST_ATTRIBUTE_KEY,
                                result));
            } catch (Exception ex) {
                LOGGER.debug("Error during serialization debug", ex);
            }
        }
        return false;
    }

    public static void serializeAndDeserialized(VaadinSession vaadinSession,
            Consumer<Result> onComplete) {
        WrappedSession session = vaadinSession.getSession();
        // Work on a copy of the session to avoid overwriting attributes
        DebugHttpSession debugHttpSession = new DebugHttpSession(session);
        Job job = new Job(session.getId());
        DebugBackendConnector connector = new DebugBackendConnector(job);
        SessionSerializer serializer = new SessionSerializer(connector,
                new DebugTransientHandler(job));
        try {
            trySerialize(serializer, debugHttpSession, job);
            SessionInfo info = connector.waitForCompletion();
            if (info != null) {
                debugHttpSession = new DebugHttpSession(
                        "DEBUG-DESERIALIZE-" + session.getId());
                tryDeserialize(serializer, info, debugHttpSession, job);
            }
        } finally {
            Result result = job.complete();
            StringBuilder message = new StringBuilder(
                    "Session serialization attempt completed in ")
                            .append(result.getDuration())
                            .append(" ms with outcomes: ")
                            .append(result.getOutcomes());
            List<String> errors = result.getErrors();
            if (!errors.isEmpty()) {
                message.append(System.lineSeparator())
                        .append(System.lineSeparator())
                        .append("ERRORS DURING SERIALIZATION/DESERIALIZATION PROCESS:")
                        .append(System.lineSeparator())
                        .append("====================================================")
                        .append(System.lineSeparator())
                        .append(String.join(
                                System.lineSeparator() + System.lineSeparator(),
                                errors));
            }
            Set<String> notSerializableClasses = result
                    .getNotSerializableClasses();
            if (!notSerializableClasses.isEmpty()) {
                message.append(System.lineSeparator())
                        .append(System.lineSeparator())
                        .append("NOT SERIALIZABLE CLASSES FOUND:")
                        .append(System.lineSeparator())
                        .append("===============================")
                        .append(System.lineSeparator())
                        .append(System.lineSeparator())
                        .append(String.join(System.lineSeparator(),
                                notSerializableClasses));
            }
            LOGGER.info(message.toString()); // NOSONAR
            onComplete.accept(result);
        }
    }

    private static void trySerialize(SessionSerializer serializer,
            HttpSession session, Job job) {
        try {
            serializer.serialize(session);
        } catch (Exception e) {
            job.serializationFailed(e);
            LOGGER.error("Test Session serialization failed", e);
        }
    }

    private static void tryDeserialize(SessionSerializer serializer,
            SessionInfo info, HttpSession debugHttpSession, Job job) {
        try {
            serializer.deserialize(info, debugHttpSession);
            job.deserialized();
        } catch (Exception e) {
            job.deserializationFailed(e);
            LOGGER.error("Test Session Deserialization failed", e);
        }
    }

    /**
     * {@link VaadinServiceInitListener} implementation that installs the
     * {@link SerializationDebugRequestHandler} if the application is running in
     * development mode and session serialization debug is enabled by setting
     * the {@literal devmode.sessionSerialization.debug} configuration property
     * to {@literal true}.
     */
    public static class InitListener implements VaadinServiceInitListener {

        @Override
        public void serviceInit(ServiceInitEvent serviceInitEvent) {
            ApplicationConfiguration appConfiguration = ApplicationConfiguration
                    .get(serviceInitEvent.getSource().getContext());
            boolean productionMode = appConfiguration.isProductionMode();
            boolean serializationDebugEnabled = appConfiguration
                    .isDevModeSessionSerializationEnabled();
            if (productionMode) {
                LoggerFactory.getLogger(InitListener.class).warn(
                        "SerializationDebugRequestHandler cannot be installed in production mode");
            } else if (!serializationDebugEnabled) {
                LoggerFactory.getLogger(InitListener.class).warn(
                        "To install SerializationDebugRequestHandler enable session serialization setting 'vaadin.devmode.sessionSerialization.enabled=true'");
            } else {
                LoggerFactory.getLogger(InitListener.class).info(
                        "Installing SerializationDebugRequestHandler for session serialization debug");
                serviceInitEvent.addRequestHandler(
                        new SerializationDebugRequestHandler());
            }
        }
    }

    /**
     * Represent the result of the test serialization/deserialization process.
     */
    public static class Result implements Serializable {
        private final String sessionId;
        private final String storageKey;
        private final LinkedHashSet<Outcome> outcomes;
        private final long duration;
        private final Map<String, List<String>> messages;

        private Result(String sessionId, String storageKey,
                Set<Outcome> outcomes, long duration,
                Map<String, List<String>> messages) {
            this.sessionId = sessionId;
            this.storageKey = storageKey;
            this.outcomes = new LinkedHashSet<>(outcomes);
            this.duration = duration;
            this.messages = messages;
        }

        /**
         * Gets the identifier of the HTTP session under test.
         *
         * @return the identifier of the HTTP session under test.
         */
        public String getSessionId() {
            return sessionId;
        }

        /**
         * Gets the identifier of the session data on the storage backend.
         *
         * @return the identifier of the session data on the storage backend.
         */
        public String getStorageKey() {
            return storageKey;
        }

        /**
         * Gets the outcome of the serialization/deserialization process.
         *
         * @return the outcome of the serialization/deserialization process.
         */
        public Set<Outcome> getOutcomes() {
            return outcomes;
        }

        /**
         * Gets the approximate duration of the process expressed in
         * milliseconds.
         *
         * @return approximate duration of the process expressed in
         *         milliseconds.
         */
        public long getDuration() {
            return duration;
        }

        /**
         * Gets the list of not serializable classes detected.
         *
         * @return list of not serializable classes detected.
         */
        public Set<String> getNotSerializableClasses() {
            return new LinkedHashSet<>(messages.getOrDefault(
                    Outcome.NOT_SERIALIZABLE_CLASSES.name(),
                    Collections.emptyList()));
        }

        /**
         * Gets the list of not serialized lambda classes detected.
         *
         * @return list of not serialized lambda detected.
         */
        public Set<String> getSerializedLambdas() {
            return new LinkedHashSet<>(messages.getOrDefault(
                    Outcome.NOT_SERIALIZABLE_CLASSES.name(),
                    Collections.emptyList()));
        }

        /**
         * Gets serialization process errors.
         *
         * @return serialization process errors.
         */
        public List<String> getErrors() {
            return messages.getOrDefault(Job.CATEGORY_ERRORS,
                    Collections.emptyList());
        }
    }

    private static class Job {

        private static final Pattern SERIALIZEDLAMBDA_CANNOT_ASSIGN = Pattern
                .compile(
                        "cannot assign instance of java.lang.invoke.SerializedLambda to field [^ ]+ of type ([^ ]+) in instance [^ ]+");
        private static final Pattern SERIALIZEDLAMBDA_CANNOT_CAST = Pattern
                .compile(
                        "class java.lang.invoke.SerializedLambda cannot be cast to class ([^ ]+)( |$)");

        public static final String CATEGORY_ERRORS = "ERRORS";
        private final String sessionId;
        private final long startTimeNanos;
        private final Set<Outcome> outcome = new LinkedHashSet<>();
        private final Map<String, List<String>> messages = new LinkedHashMap<>();
        private String storageKey;

        private long deserializationDepth = 0;
        private boolean popDeserializationStack = false;
        private final Map<Long, List<String>> deserializationStack = new TreeMap<>();

        private Job(String sessionId) {
            this.sessionId = sessionId;
            this.startTimeNanos = System.nanoTime();
        }

        public void serializationStarted() {
            // No-Op
            outcome.add(Outcome.SERIALIZATION_FAILED);
        }

        void notSerializable(Class<?> clazz) {
            StringBuilder info = new StringBuilder(clazz.getName());
            if (clazz.isSynthetic() && !clazz.isAnonymousClass()
                    && !clazz.isLocalClass()
                    && clazz.getSimpleName().contains("$$Lambda$")
                    && clazz.getInterfaces().length == 1) {
                // Additional details for lamdba expressions
                Class<?> samInterface = clazz.getInterfaces()[0];
                Method samMethod = samInterface.getMethods()[0];
                StringJoiner sj = new StringJoiner(",",
                        samMethod.getName() + "(", ")");
                for (Class<?> parameterType : samMethod.getParameterTypes()) {
                    sj.add(parameterType.getTypeName());
                }
                info.append(" [ SAM interface: ").append(samInterface.getName())
                        .append(".").append(sj).append(" ]");
            }
            log(Outcome.NOT_SERIALIZABLE_CLASSES.name(), info.toString());
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
            log(CATEGORY_ERRORS, Outcome.SERIALIZATION_FAILED.name() + ": "
                    + ex.getMessage());
        }

        void deserialized() {
            outcome.remove(Outcome.DESERIALIZATION_FAILED);
        }

        void deserializationFailed(Exception ex) {
            outcome.add(Outcome.DESERIALIZATION_FAILED);
            log(CATEGORY_ERRORS, Outcome.DESERIALIZATION_FAILED.name() + ": "
                    + ex.getMessage());
            dumpDeserializationStack()
                    .ifPresent(message -> log(CATEGORY_ERRORS, message));
            if (ex instanceof ClassCastException
                    && ex.getMessage()
                            .contains(SerializedLambda.class.getName())
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
                            .map(lambda -> "\t" + lambda).collect(
                                    Collectors.joining(System.lineSeparator()));
                    if (!bestCandidates.isEmpty()) {
                        log(CATEGORY_ERRORS,
                                "SERIALIZED LAMBDA CLASS CAST EXCEPTION BEST CANDIDATES:"
                                        + System.lineSeparator()
                                        + "======================================================="
                                        + System.lineSeparator()
                                        + bestCandidates);
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

        void pushDeserialization(long depth, String entry) {
            deserializationStack
                    .computeIfAbsent(depth, unused -> new ArrayList<>())
                    .add(entry);
            while (popDeserializationStack && deserializationDepth > depth) {
                deserializationStack.remove(deserializationDepth--);
            }
            popDeserializationStack = false;
            deserializationDepth = depth;
        }

        void popDeserialization() {
            popDeserializationStack = true;
        }

        Optional<String> dumpDeserializationStack() {
            if (!deserializationStack.isEmpty()) {
                StringBuilder builder = new StringBuilder(
                        "DESERIALIZATION STACK. Process failed at depth ")
                                .append(deserializationDepth)
                                .append(System.lineSeparator());
                for (Map.Entry<Long, List<String>> entry : deserializationStack
                        .entrySet()) {
                    String indent = " ".repeat(entry.getKey().intValue() * 2);
                    entry.getValue().stream().map(
                            clazz -> indent + clazz + System.lineSeparator())
                            .forEach(builder::append);
                }
                return Optional.of(builder.toString());
            }
            return Optional.empty();
        }

        Result complete() {
            if (outcome.isEmpty()) {
                outcome.add(Outcome.SUCCESS);
            }
            long duration = TimeUnit.NANOSECONDS
                    .toMillis(System.nanoTime() - this.startTimeNanos);
            return new Result(sessionId, storageKey, outcome, duration,
                    messages);
        }

        void log(String category, String message) {
            messages.computeIfAbsent(category, unused -> new ArrayList<>())
                    .add(message);
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
    }

    /**
     * Outcome of the serialization/deserialization process.
     */
    public enum Outcome {
        /**
         * Process has not started.
         */
        NOT_STARTED,
        /**
         * Not serializable classes found during serialization phase
         */
        NOT_SERIALIZABLE_CLASSES,
        /**
         * Process failed during serialization phase
         */
        SERIALIZATION_FAILED,
        /**
         * Process failed during deserialization phase
         */
        DESERIALIZATION_FAILED,
        /**
         * Process completed successfully
         */
        SUCCESS
    }

    private static class DebugBackendConnector implements BackendConnector {

        private final Job job;

        public DebugBackendConnector(Job job) {
            this.job = job;
        }

        private SessionInfo sessionInfo;

        private CountDownLatch latch;

        @Override
        public void sendSession(SessionInfo sessionInfo) {
            this.sessionInfo = sessionInfo;
        }

        @Override
        public SessionInfo getSession(String clusterKey) {
            return sessionInfo;
        }

        @Override
        public void markSerializationStarted(String clusterKey) {
            latch = new CountDownLatch(1);
            job.serializationStarted();
        }

        @Override
        public void markSerializationComplete(String clusterKey) {
            job.serialized(sessionInfo);
            latch.countDown();
        }

        private SessionInfo waitForCompletion() {
            int timeout = 50000;
            try {
                if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                    LOGGER.error(
                            "Session Serialization did not completed in {} ms.",
                            timeout);
                }
            } catch (Exception e) { // NOSONAR
                LOGGER.error("Testing of Session Serialization failed", e);
            }
            return sessionInfo;
        }

    }

    private static class DebugTransientHandler
            implements TransientHandler, TransientHandler.DebugMode {

        private final Job job;

        DebugTransientHandler(Job job) {
            this.job = job;
        }

        @Override
        public void inject(Object object,
                List<TransientDescriptor> transients) {
            // NO-OP
        }

        @Override
        public List<TransientDescriptor> inspect(Object object) {
            return Collections.emptyList();
        }

        @Override
        public Optional<Serializable> onNotSerializableFound(Object object) {
            job.notSerializable(object.getClass());
            return Optional.of(NULLIFY);
        }

        @Override
        public void onSerialize(Object object) {
            if (object instanceof SerializedLambda) {
                SerializedLambda cast = (SerializedLambda) object;
                String description = String.format(
                        "[%s=%s, %s=%s, %s=%s:%s, "
                                + "%s=%s.%s:%s, %s=%s, %s=%d]",
                        "capturingClass", cast.getCapturingClass(),
                        "functionalInterfaceClass",
                        cast.getFunctionalInterfaceClass(),
                        "functionalInterfaceMethod",
                        cast.getFunctionalInterfaceMethodName(),
                        cast.getFunctionalInterfaceMethodSignature(),
                        "implementation", cast.getImplClass(),
                        cast.getImplMethodName(), cast.getImplMethodSignature(),
                        "instantiatedMethodType",
                        cast.getInstantiatedMethodType(), "numCaptured",
                        cast.getCapturedArgCount());
                job.log(SerializedLambda.class.getName(), description);
            }
        }

        @Override
        public void onDeserialize(Class<?> type, long depth) {
            String typeAndFields = Arrays
                    .stream(ObjectStreamClass.lookupAny(type).getFields())
                    .map(field -> String.format("%s (%s)", field.getName(),
                            field.getType()))
                    .collect(Collectors.joining(", ", type + " [", "]"));
            job.pushDeserialization(depth, typeAndFields);
        }

        @Override
        public void onDeserialized(Object object) {
            job.popDeserialization();
        }
    }

    private static class DebugHttpSession implements HttpSession {

        private final Map<String, Object> storage = new HashMap<>();
        private final String sessionId;

        DebugHttpSession(String sessionId) {
            this.sessionId = sessionId;
        }

        DebugHttpSession(WrappedSession source) {
            this.sessionId = "DEBUG-SERIALIZE-" + source.getId();
            source.getAttributeNames()
                    .forEach(key -> storage.put(key, source.getAttribute(key)));
            storage.put(CurrentKey.COOKIE_NAME,
                    UUID.randomUUID().toString() + "_SOURCE:" + source.getId());
        }

        @Override
        public long getCreationTime() {
            return 0;
        }

        @Override
        public String getId() {
            return sessionId;
        }

        @Override
        public long getLastAccessedTime() {
            return 0;
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public void setMaxInactiveInterval(int interval) {
            // Not Implemented
        }

        @Override
        public int getMaxInactiveInterval() {
            return 0;
        }

        @Override
        public HttpSessionContext getSessionContext() {
            return null;
        }

        @Override
        public Object getAttribute(String name) {
            return storage.get(name);
        }

        @Override
        public Object getValue(String name) {
            return getAttribute(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(storage.keySet());
        }

        @Override
        public String[] getValueNames() {
            return storage.keySet().toArray(new String[0]);
        }

        @Override
        public void setAttribute(String name, Object value) {
            storage.put(name, value);
        }

        @Override
        public void putValue(String name, Object value) {
            setAttribute(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            storage.remove(name);
        }

        @Override
        public void removeValue(String name) {
            removeAttribute(name);
        }

        @Override
        public void invalidate() {
            // Not Implemented
        }

        @Override
        public boolean isNew() {
            return false;
        }
    }
}
