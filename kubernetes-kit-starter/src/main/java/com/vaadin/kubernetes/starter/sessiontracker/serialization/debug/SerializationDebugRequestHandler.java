package com.vaadin.kubernetes.starter.sessiontracker.serialization.debug;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.kubernetes.starter.sessiontracker.CurrentKey;
import com.vaadin.kubernetes.starter.sessiontracker.SessionSerializer;
import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionInfo;
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
 * {@literal vaadin.devmode.sessionSerialization.enabled} configuration property
 * is set to {@literal true}.
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
            SessionInfo info = connector.waitForCompletion(LOGGER);
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
            List<String> notSerializableClasses = result
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
                    UUID.randomUUID() + "_SOURCE:" + source.getId());
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
