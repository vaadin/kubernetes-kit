/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker;

import jakarta.servlet.http.HttpSession;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedHttpSession;
import com.vaadin.flow.server.WrappedSession;
import com.vaadin.kubernetes.starter.ProductUtils;
import com.vaadin.kubernetes.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionExpirationPolicy;
import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionInfo;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.SerializationInputStream;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.SerializationOutputStream;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.SerializationStreamFactory;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientHandler;

/**
 * Component responsible for replicating HTTP session attributes to a
 * distributed storage.
 *
 * HTTP session attributes are serialized and deserialized by
 * {@link SessionSerializer}, using Java Serialization specifications.
 *
 * Transient fields of serialized objects are inspected by a pluggable
 * {@link TransientHandler} component to gather metadata that is stored along
 * the session attributes and then used during deserialization to populate the
 * fields on the new instances.
 *
 * When serializing an HTTP session, {@link SessionSerializer} marks the current
 * session as pending before starting an async serialization process.
 *
 * Pending state is hold by the backend connector and removed once the data has
 * been written on the distributed storage.
 *
 * Concurrent attempts are ignored until the pending state is cleared. The
 * operation is safe and will not lose any Vaadin related data (UI or
 * {@link VaadinSession} attributes) because the serializer is always working on
 * the same {@link VaadinSession} instance.
 *
 * However, it may potentially be possible to lose attributes that are directly
 * added on the HTTP session, because the asynchronous job does not work
 * directly on the HttpSession, but on a map that references the original
 * attributes. For the same reason, the serializer may persist attributes that a
 * request has removed during the pending state.
 *
 * {@link VaadinSession} data integrity is granted by an optimistic/pessimistic
 * handling, based on {@link VaadinSession} lock timestamp.
 *
 * Session serialization process works as following:
 *
 * <ul>
 * <li>it first checks that VaadinSession is currently unlocked. If so it
 * performs serialization</li>
 * <li>if VaadinSession is locked it schedules another attempt</li>
 * <li>Once data serialization is completed, it checks if the VaadinSession has
 * been locked and unlocked in the meanwhile</li>
 * <li>If so, it discards serialized data and schedules another attempt</li>
 * <li>If after a timeout of 30 seconds it has not been possible to complete the
 * serialization without VaadinSession locks/unlocks it falls back to a
 * pessimistic approach</li>
 * <li>Pessimistic approach locks the VaadinSession during the
 * serialization</li>
 * <li>Finally serialized data is written to the distributes storage</li>
 * </ul>
 *
 * In case of a server shutdown, it waits for pending session serializations to
 * complete.
 */
public class SessionSerializer
        implements ApplicationListener<ContextClosedEvent> {

    static {
        ProductUtils.markAsUsed(SessionSerializer.class.getSimpleName());
    }

    private static final long OPTIMISTIC_SERIALIZATION_TIMEOUT_MS = 30000;

    private final ExecutorService executorService = Executors
            .newFixedThreadPool(4, new SerializationThreadFactory());

    private final ConcurrentHashMap<String, Boolean> pending = new ConcurrentHashMap<>();

    private final BackendConnector backendConnector;

    // (sessionId, clusterKey) -> TransientHandler
    private final BiFunction<String, String, TransientHandler> handlerProvider;

    private final long optimisticSerializationTimeoutMs;

    private final SessionSerializationCallback sessionSerializationCallback;

    private final SessionExpirationPolicy sessionExpirationPolicy;

    private final SerializationStreamFactory serializationStreamFactory;

    private Predicate<Class<?>> injectableFilter = type -> true;

    /**
     * Creates a new {@link SessionSerializer}.
     *
     * @param backendConnector
     *            backend connector to store serialized data on distributed
     *            storage.
     * @param transientHandler
     *            handler to inspect and inject transient fields.
     * @param sessionSerializationCallback
     *            callbacks for successful serialization and deserialization or
     *            when an error happens
     */
    public SessionSerializer(BackendConnector backendConnector,
            TransientHandler transientHandler,
            SessionExpirationPolicy sessionExpirationPolicy,
            SessionSerializationCallback sessionSerializationCallback,
            SerializationStreamFactory serializationStreamFactory) {
        this(backendConnector, (sessionId, clusterKey) -> transientHandler,
                sessionExpirationPolicy, sessionSerializationCallback,
                serializationStreamFactory);
    }

    /**
     * Creates a new {@link SessionSerializer}.
     * <p>
     * </p>
     * The {@link TransientHandler} provider is called when serialization
     * process start, providing {@code session ID} and {@code cluster key}, to
     * allow the implementor to track or provide actions based on the current
     * processing.
     * <p>
     * </p>
     * This constructor is basically an internal API, meant to be used only by
     * the serialization debug tool.
     * <p>
     * </p>
     * For internal use only,
     *
     * @param backendConnector
     *            backend connector to store serialized data on distributed
     *            storage.
     * @param transientHandlerProvider
     *            provides handler to inspect and inject transient fields.
     * @param sessionSerializationCallback
     *            callbacks for successful serialization and deserialization or
     *            when an error happens
     */
    public SessionSerializer(BackendConnector backendConnector,
            BiFunction<String, String, TransientHandler> transientHandlerProvider,
            SessionExpirationPolicy sessionExpirationPolicy,
            SessionSerializationCallback sessionSerializationCallback,
            SerializationStreamFactory serializationStreamFactory) {
        this.backendConnector = backendConnector;
        this.handlerProvider = transientHandlerProvider;
        this.sessionSerializationCallback = sessionSerializationCallback;
        this.sessionExpirationPolicy = sessionExpirationPolicy;
        this.serializationStreamFactory = serializationStreamFactory;
        optimisticSerializationTimeoutMs = OPTIMISTIC_SERIALIZATION_TIMEOUT_MS;
    }

    // Visible for test
    SessionSerializer(BackendConnector backendConnector,
            TransientHandler transientHandler,
            SessionExpirationPolicy sessionExpirationPolicy,
            SessionSerializationCallback sessionSerializationCallback,
            long optimisticSerializationTimeoutMs,
            SerializationStreamFactory serializationStreamFactory) {
        this.backendConnector = backendConnector;
        this.optimisticSerializationTimeoutMs = optimisticSerializationTimeoutMs;
        this.sessionSerializationCallback = sessionSerializationCallback;
        this.sessionExpirationPolicy = sessionExpirationPolicy;
        this.handlerProvider = (sessionId, clusterKey) -> transientHandler;
        this.serializationStreamFactory = serializationStreamFactory;
    }

    /**
     * Provide a filter to restrict classes suitable for transients field
     * inspection.
     *
     * If {@literal null} all classes are inspected. This is the default
     * behavior.
     *
     * @param injectableFilter
     *            a filter to restrict classes suitable for transients field *
     *            inspection.
     */
    public void setInjectableFilter(Predicate<Class<?>> injectableFilter) {
        this.injectableFilter = injectableFilter;
    }

    /**
     * Serializes the given HTTP session and stores data on a distributed
     * storage.
     *
     * @param session
     *            the HTTP session.
     */
    public void serialize(HttpSession session) {
        serialize(new WrappedHttpSession(session));
    }

    /**
     * Serializes the given Vaadin Wrapped session and stores data on a
     * distributed storage.
     *
     * @param session
     *            the Vaadin Wrapped session.
     */
    public void serialize(WrappedSession session) {
        Map<String, Object> values = session.getAttributeNames().stream()
                .collect(Collectors.toMap(Function.identity(),
                        session::getAttribute));
        Duration timeToLive = sessionExpirationPolicy
                .apply(session.getMaxInactiveInterval());
        queueSerialization(session.getId(), timeToLive, values);
    }

    /**
     * Deserializes binary data from the distributed storage into the given HTTP
     * session.
     *
     * @param sessionInfo
     *            session data from distributed storage.
     * @param session
     *            the HTTP session
     *
     * @throws Exception
     *             any of the deserialization related exceptions.
     */
    public void deserialize(SessionInfo sessionInfo, HttpSession session)
            throws Exception {
        Map<String, Object> values = doDeserialize(sessionInfo,
                session.getId());

        for (Entry<String, Object> entry : values.entrySet()) {
            session.setAttribute(entry.getKey(), entry.getValue());
        }
    }

    private void queueSerialization(String sessionId, Duration timeToLive,
            Map<String, Object> attributes) {
        if (pending.containsKey(sessionId)) {
            // This session will be serialized again soon enough
            getLogger().debug(
                    "Ignoring serialization request for session {} as the session is already being serialized",
                    sessionId);
            return;
        }
        String clusterKey = getClusterKey(attributes);
        getLogger().debug(
                "Starting asynchronous serialization of session {} with distributed key {}",
                sessionId, clusterKey);
        pending.put(sessionId, true);
        // Backend operations are performed asynchronously to prevent the UI to
        // freeze in case of errors, timeouts or slow performance.
        // Current session is immediately marked as 'serialization pending',
        // because if 'markSerializationStarted' is hanging, it does not make
        // sense to retry the operation instantly.
        CompletableFuture.runAsync(
                () -> backendConnector.markSerializationStarted(clusterKey),
                executorService).handle((unused, error) -> {
                    if (error != null) {
                        getLogger().debug(
                                "Failed marking serialization start for of session {} with distributed key {}",
                                sessionId, clusterKey, error);
                    } else {
                        Consumer<SessionInfo> whenSerialized = sessionInfo -> {
                            if (sessionInfo != null) {
                                backendConnector.sendSession(sessionInfo);
                            }
                            backendConnector
                                    .markSerializationComplete(clusterKey);
                        };
                        handleSessionSerialization(sessionId, timeToLive,
                                attributes, whenSerialized);
                    }
                    return null;
                }).whenComplete((unused, error) -> {
                    pending.remove(sessionId);
                    if (error != null) {
                        getLogger().error("Serialization of session {} failed",
                                sessionId, error);
                    }
                });
    }

    private void handleSessionSerialization(String sessionId,
            Duration timeToLive, Map<String, Object> attributes,
            Consumer<SessionInfo> whenSerialized) {
        boolean unrecoverableError = false;
        String clusterKey = getClusterKey(attributes);
        try {
            checkUnserializableWrappers(attributes);
            long start = System.currentTimeMillis();
            long timeout = start + optimisticSerializationTimeoutMs;
            getLogger().debug(
                    "Optimistic serialization of session {} with distributed key {} started",
                    sessionId, clusterKey);
            while (System.currentTimeMillis() < timeout) {
                SessionInfo info = serializeOptimisticLocking(sessionId,
                        timeToLive, attributes);
                if (info != null) {
                    pending.remove(sessionId); // Is this a race condition?
                    getLogger().debug(
                            "Optimistic serialization of session {} with distributed key {} completed",
                            sessionId, clusterKey);
                    whenSerialized.accept(info);
                    return;
                }
            }
        } catch (PessimisticSerializationRequiredException e) {
            if (e instanceof UnserializableComponentWrapperFoundException) {
                getLogger().debug(e.getMessage());
            } else {
                getLogger().warn(
                        "Optimistic serialization of session {} with distributed key {} cannot be completed"
                                + " because VaadinSession lock is required. Switching to pessimistic locking.",
                        sessionId, clusterKey, e);
            }
        } catch (NotSerializableException e) {
            getLogger().error(
                    "Optimistic serialization of session {} with distributed key {} failed,"
                            + " some attribute is not serializable. Giving up immediately since the error is not recoverable",
                    sessionId, clusterKey, e);
            unrecoverableError = true;
        } catch (IOException e) {
            getLogger().warn(
                    "Optimistic serialization of session {} with distributed key {} failed",
                    sessionId, clusterKey, e);
            unrecoverableError = true;
        }

        pending.remove(sessionId);
        SessionInfo sessionInfo = null;
        if (!unrecoverableError) { // NOSONAR
            // Serializing using optimistic locking failed for a long time so be
            // pessimistic and get it done
            sessionInfo = serializePessimisticLocking(sessionId, timeToLive,
                    attributes);
        }
        whenSerialized.accept(sessionInfo);
    }

    private SessionInfo serializePessimisticLocking(String sessionId,
            Duration timeToLive, Map<String, Object> attributes) {
        long start = System.currentTimeMillis();
        String clusterKey = getClusterKey(attributes);
        Set<ReentrantLock> locks = getLocks(attributes);
        for (ReentrantLock lock : locks) {
            lock.lock();
        }
        try {
            beforeSerializePessimistic(attributes);
            return doSerialize(sessionId, timeToLive, attributes);
        } catch (Exception e) {
            getLogger().error(
                    "An error occurred during pessimistic serialization of session {} with distributed key {} ",
                    sessionId, clusterKey, e);
        } finally {
            afterSerializePessimistic(attributes);
            for (ReentrantLock lock : locks) {
                lock.unlock();
            }
            getLogger().debug(
                    "Pessimistic serialization of session {} with distributed key {} completed in {}ms",
                    sessionId, clusterKey, System.currentTimeMillis() - start);
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private void checkUnserializableWrappers(Map<String, Object> attributes) {
        Consumer<UnserializableComponentWrapper> action = c -> {
            throw new UnserializableComponentWrapperFoundException(
                    "Pessimistic serialization required because at least one "
                            + UnserializableComponentWrapper.class.getName()
                            + " is in the UI tree");
        };
        Set<ReentrantLock> locks = getLocks(attributes);
        for (ReentrantLock lock : locks) {
            lock.lock();
        }
        try {
            getUIs(attributes).forEach(ui -> UnserializableComponentWrapper
                    .doWithWrapper(ui, action));
        } finally {
            for (ReentrantLock lock : locks) {
                lock.unlock();
            }
        }
    }

    private void beforeSerializePessimistic(Map<String, Object> attributes) {
        getUIs(attributes)
                .forEach(UnserializableComponentWrapper::beforeSerialization);
    }

    private void afterSerializePessimistic(Map<String, Object> attributes) {
        getUIs(attributes)
                .forEach(UnserializableComponentWrapper::afterSerialization);
    }

    private List<UI> getUIs(Map<String, Object> attributes) {
        return attributes.values().stream()
                .filter(o -> o instanceof VaadinSession)
                .map(VaadinSession.class::cast)
                .flatMap(s -> s.getUIs().stream()).toList();
    }

    private Set<ReentrantLock> getLocks(Map<String, Object> attributes) {
        Set<ReentrantLock> locks = new HashSet<>();
        for (String key : attributes.keySet()) {
            if (key.startsWith("com.vaadin.flow.server.VaadinSession")) {
                String serviceName = key.substring(
                        "com.vaadin.flow.server.VaadinSession".length() + 1);
                String lockKey = serviceName + ".lock";
                Object lockAttribute = attributes.get(lockKey);
                if (lockAttribute instanceof ReentrantLock lock) {
                    locks.add(lock);
                }

            }
        }
        return locks;
    }

    private SessionInfo serializeOptimisticLocking(String sessionId,
            Duration timeToLive, Map<String, Object> attributes)
            throws IOException {
        String clusterKey = getClusterKey(attributes);
        try {
            long latestLockTime = findNewestLockTime(attributes);
            long latestUnlockTime = findNewestUnlockTime(attributes);

            if (latestLockTime > latestUnlockTime) {
                // The session is locked
                getLogger().trace(
                        "Optimistic serialization of session {} with distributed key {} failed, session is locked. Will retry",
                        sessionId, clusterKey);
                return null;
            }

            SessionInfo info = doSerialize(sessionId, timeToLive, attributes);

            long latestUnlockTimeCheck = findNewestUnlockTime(attributes);

            if (latestUnlockTime != latestUnlockTimeCheck) {
                // Somebody modified the session during serialization and the
                // result cannot be used
                getLogger().trace(
                        "Optimistic serialization of session {} with distributed key {} failed, "
                                + "somebody modified the session during serialization ({} != {}). Will retry",
                        sessionId, clusterKey, latestUnlockTime,
                        latestUnlockTimeCheck);
                return null;
            }
            logSessionDebugInfo("Serialized session " + sessionId
                    + " with distributed key " + clusterKey, attributes);
            return info;
        } catch (NotSerializableException
                | PessimisticSerializationRequiredException e) {
            throw e;
        } catch (Exception e) {
            getLogger().trace(
                    "Optimistic serialization of session {} with distributed key {} failed,"
                            + " a problem occurred during serialization. Will retry",
                    sessionId, clusterKey, e);
            return null;
        }
    }

    private void logSessionDebugInfo(String prefix,
            Map<String, Object> attributes) {
        StringBuilder info = new StringBuilder();
        for (String key : attributes.keySet()) {
            Object value = attributes.get(key);
            if (value instanceof VaadinSession session) {
                try {
                    for (UI ui : session.getUIs()) {
                        info.append("[UI ").append(ui.getUIId())
                                .append(", last client message: ")
                                .append(ui.getInternals()
                                        .getLastProcessedClientToServerId())
                                .append(", server sync id: ")
                                .append(ui.getInternals().getServerSyncId())
                                .append("]");
                    }
                } catch (Exception ex) {
                    // getting UIs may fail in development mode due to null lock
                    // (deserialization) or session not locked (serialization)
                    // ignoring for now since it is just a log
                    info.append(
                            "[ VaadinSession not accessible without locking ]");
                }
            }
        }
        getLogger().trace("{} UIs: {}", prefix, info);
    }

    private long findNewestLockTime(Map<String, Object> attributes) {
        long latestLock = 0L;
        for (Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() instanceof VaadinSession session) {
                latestLock = Math.max(latestLock, session.getLastLocked());
            }
        }
        return latestLock;
    }

    private long findNewestUnlockTime(Map<String, Object> attributes) {
        long latestUnlock = 0L;
        for (Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() instanceof VaadinSession session) {
                latestUnlock = Math.max(latestUnlock,
                        session.getLastUnlocked());
            }
        }
        return latestUnlock;
    }

    private SessionInfo doSerialize(String sessionId, Duration timeToLive,
            Map<String, Object> attributes) throws Exception {
        long start = System.currentTimeMillis();
        String clusterKey = getClusterKey(attributes);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TransientHandler transientHandler = handlerProvider.apply(sessionId,
                clusterKey);
        try (SerializationOutputStream outStream = serializationStreamFactory
                .createOutputStream(out, transientHandler, injectableFilter)) {
            outStream.writeWithTransients(attributes);
            sessionSerializationCallback.onSerializationSuccess();
        } catch (Exception ex) {
            sessionSerializationCallback.onSerializationError(ex);
            throw ex;
        }

        SessionInfo info = new SessionInfo(clusterKey, timeToLive,
                out.toByteArray());

        getLogger().debug(
                "Serialization of attributes {} for session {} with distributed key {} completed in {}ms ({} bytes)",
                attributes.keySet(), sessionId, info.getClusterKey(),
                System.currentTimeMillis() - start, info.getData().length);
        return info;
    }

    private String getClusterKey(Map<String, Object> attributes) {
        return (String) attributes.get(CurrentKey.COOKIE_NAME);
    }

    private Map<String, Object> doDeserialize(SessionInfo sessionInfo,
            String sessionId) throws Exception {
        byte[] data = sessionInfo.getData();
        long start = System.currentTimeMillis();

        // Is this needed?
        ClassLoader contextLoader = Thread.currentThread()
                .getContextClassLoader();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        Map<String, Object> attributes;
        TransientHandler transientHandler = handlerProvider.apply(sessionId,
                sessionInfo.getClusterKey());

        try (SerializationInputStream inStream = serializationStreamFactory
                .createInputStream(in, transientHandler)) {
            attributes = inStream.readWithTransients();
            sessionSerializationCallback.onDeserializationSuccess();
        } catch (Exception ex) {
            sessionSerializationCallback.onDeserializationError(ex);
            throw ex;
        } finally {
            Thread.currentThread().setContextClassLoader(contextLoader);
        }
        logSessionDebugInfo("Deserialized session", attributes);

        getLogger().debug(
                "Deserialization of attributes {} for session {} with distributed key {} completed in {}ms",
                attributes.keySet(), sessionId, sessionInfo.getClusterKey(),
                System.currentTimeMillis() - start);
        return attributes;
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(SessionSerializer.class);
    }

    void waitForSerialization() {
        while (!pending.isEmpty()) {
            getLogger().info("Waiting for {} sessions to be serialized: {}",
                    pending.size(), pending.keySet());
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        // This is using a context closed event and not a predestroy hook to
        // ensure that
        // the server has not shut down before this and made the session
        // unavailable
        waitForSerialization();
        executorService.shutdown();
    }

    private static class SerializationThreadFactory implements ThreadFactory {

        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "sessionSerializer-worker-" + hashCode()
                    + "-" + threadNumber.getAndIncrement());
        }

    }
}
