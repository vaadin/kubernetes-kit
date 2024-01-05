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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.server.RequestHandler;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedHttpSession;
import com.vaadin.flow.server.WrappedSession;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import com.vaadin.kubernetes.starter.SerializationProperties;
import com.vaadin.kubernetes.starter.sessiontracker.CurrentKey;
import com.vaadin.kubernetes.starter.sessiontracker.SessionSerializer;
import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionInfo;
import com.vaadin.kubernetes.starter.ui.SessionDebugNotifier;

import static com.vaadin.kubernetes.starter.SerializationProperties.DEFAULT_SERIALIZATION_TIMEOUT_MS;

/**
 * A {@link RequestHandler} implementation that performs a check on HTTP session
 * serialization and deserialization.
 *
 * The request handler is executed only in development mode and if the
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

    private static final String SERIALIZATION_TIMEOUT_PROPERTY = "vaadin.serialization.timeout";

    private final SerializationProperties serializationProperties;

    public SerializationDebugRequestHandler(SerializationProperties serializationProperties) {
        this.serializationProperties = serializationProperties;
    }

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
                VaadinService vaadinService = vaadinSession.getService();
                vaadinSession.accessSynchronously(() -> {
                    SerializableConsumer<Result> onSuccess = null;
                    UI ui = vaadinService.findUI(vaadinRequest);
                    if (ui != null) {
                        boolean pushEnabled = ui.getPushConfiguration()
                                .getPushMode().isEnabled();
                        SessionDebugNotifier debugNotifier = ui.getChildren()
                                .filter(SessionDebugNotifier.class::isInstance)
                                .map(SessionDebugNotifier.class::cast).findAny()
                                .orElseGet(() -> {
                                    SessionDebugNotifier notifier = new SessionDebugNotifier();
                                    ui.add(notifier);
                                    return notifier;
                                });
                        if (pushEnabled) {
                            onSuccess = ui.accessLater(
                                    debugNotifier::publishResults, () -> {
                                    });
                        }
                    }
                    int serializationTimeout = getSerializationTimeout(serializationProperties);
                    vaadinRequest.setAttribute(
                            SERIALIZATION_TEST_REQUEST_ATTRIBUTE_KEY,
                            new Runner(onSuccess, serializationTimeout));
                });

            } catch (Exception ex) {
                LOGGER.debug("Error during serialization debug", ex);
            }
        }
        return false;
    }

    static class Runner implements Consumer<HttpServletRequest> {

        private final Consumer<Result> onSuccess;
        private final int serializationTimeout;

        public Runner(Consumer<Result> onSuccess, int serializationTimeout) {
            this.onSuccess = onSuccess;
            this.serializationTimeout = serializationTimeout;
        }

        private void executeOnSuccess(Result result) {
            if (onSuccess != null) {
                try {
                    onSuccess.accept(result);
                } catch (Exception ex) {
                    // Do not interrupt the request
                    ex.printStackTrace();
                }
            }
        }

        public void accept(HttpServletRequest request) {
            HttpSession session = request.getSession(false);
            if (session != null && request.isRequestedSessionIdValid()) {
                serializeAndDeserialize(new WrappedHttpSession(session),
                        this::executeOnSuccess, serializationTimeout);
            }
        }
    }

    public static class Filter extends HttpFilter {

        private static final Logger LOGGER = LoggerFactory
                .getLogger(Filter.class);

        @Override
        protected void doFilter(HttpServletRequest req, HttpServletResponse res,
                FilterChain chain) throws IOException, ServletException {
            chain.doFilter(req, res);
            Object action = req
                    .getAttribute(SERIALIZATION_TEST_REQUEST_ATTRIBUTE_KEY);
            if (action instanceof Runner) {
                LOGGER.debug(
                        "Vaadin request processed, running Session Serialization Debug Tool");
                ((Runner) action).accept(req);
            }
        }
    }

    public static void serializeAndDeserialize(WrappedSession session,
            Consumer<Result> onComplete, int serializationTimeout) {
        // Work on a copy of the session to avoid overwriting attributes
        DebugHttpSession debugHttpSession = new DebugHttpSession(session);
        Job job = new Job(session.getId());
        DebugBackendConnector connector = new DebugBackendConnector(job);
        SessionSerializer serializer = new SessionSerializer(connector,
                new DebugTransientHandler(job));
        try {
            trySerialize(serializer, debugHttpSession, job);
            SessionInfo info = connector.waitForCompletion(serializationTimeout, LOGGER);
            if (info != null) {
                debugHttpSession = new DebugHttpSession(
                        "DEBUG-DESERIALIZE-" + session.getId());
                tryDeserialize(serializer, info, debugHttpSession, job);
            }
        } finally {
            Result result = job.complete();
            StringBuilder message = new StringBuilder(
                    "Session serialization attempt finished in ")
                    .append(result.getDuration()).append(" ms with outcomes: ")
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

    private int getSerializationTimeout(SerializationProperties properties) {
        int timeout = DEFAULT_SERIALIZATION_TIMEOUT_MS;
        if (properties != null && properties.getTimeout() > 0) {
            timeout = properties.getTimeout();
        } else {
            String timeoutStr = System.getProperty(SERIALIZATION_TIMEOUT_PROPERTY);
            if (timeoutStr != null) {
                timeout = Integer.parseInt(timeoutStr);
            }
        }
        return timeout;
    }

    private static void trySerialize(SessionSerializer serializer,
            HttpSession session, Job job) {
        try {
            serializer.serialize(session);
        } catch (Exception e) {
            job.serializationFailed(e);
            LOGGER.error("Test session serialization failed", e);
        }
    }

    private static void tryDeserialize(SessionSerializer serializer,
            SessionInfo info, HttpSession debugHttpSession, Job job) {
        try {
            serializer.deserialize(info, debugHttpSession);
            job.deserialized();
        } catch (Exception e) {
            job.deserializationFailed(e);
            LOGGER.error("Test session deserialization failed", e);
        }
    }

    /**
     * {@link VaadinServiceInitListener} implementation that installs the
     * {@link SerializationDebugRequestHandler} if the following preconditions
     * are met:
     *
     * <ul>
     * <li>application is running in development mode.</li>
     * <li>session serialization debug is enabled by setting the
     * {@literal devmode.sessionSerialization.debug} configuration property to
     * {@literal true}.</li>
     * <li>sun.io.serialization.extendedDebugInfo system property is set to
     * true.</li>
     * <li>reflection on {@code java.io} packages is allowed by adding
     * '--add-opens java.base/java.io=ALL-UNNAMED' flag to the JVM.</li>
     * </ul>
     */
    public static class InitListener implements VaadinServiceInitListener {

        public SerializationProperties serializationProperties;

        public InitListener() {
        }

        public InitListener(SerializationProperties serializationProperties) {
            this.serializationProperties = serializationProperties;
        }

        @Override
        public void serviceInit(ServiceInitEvent serviceInitEvent) {
            ApplicationConfiguration appConfiguration = ApplicationConfiguration
                    .get(serviceInitEvent.getSource().getContext());
            Logger logger = LoggerFactory.getLogger(InitListener.class);
            if (appConfiguration.isProductionMode()) {
                logger.warn(
                        "SerializationDebugRequestHandler cannot be installed in production mode");
            } else if (!appConfiguration
                    .isDevModeSessionSerializationEnabled()) {
                logger.warn(
                        "To install SerializationDebugRequestHandler enable session serialization setting 'vaadin.devmode.sessionSerialization.enabled=true'");
            } else if (!DebugMode.isTrackingAvailable(logger)) {
                logger.warn(
                        "SerializationDebugRequestHandler cannot be installed if above preconditions are not met");
            } else {
                logger.info(
                        "Installing SerializationDebugRequestHandler for session serialization debug");
                serviceInitEvent.addRequestHandler(
                        new SerializationDebugRequestHandler(serializationProperties));
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
        public Object getAttribute(String name) {
            return storage.get(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(storage.keySet());
        }

        @Override
        public void setAttribute(String name, Object value) {
            storage.put(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            storage.remove(name);
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
