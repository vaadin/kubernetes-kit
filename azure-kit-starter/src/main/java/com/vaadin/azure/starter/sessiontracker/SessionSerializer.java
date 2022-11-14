package com.vaadin.azure.starter.sessiontracker;

import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

import com.vaadin.azure.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.azure.starter.sessiontracker.backend.SessionInfo;
import com.vaadin.azure.starter.sessiontracker.serialization.TransientHandler;
import com.vaadin.azure.starter.sessiontracker.serialization.TransientInjectableObjectInputStream;
import com.vaadin.azure.starter.sessiontracker.serialization.TransientInjectableObjectOutputStream;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedHttpSession;
import com.vaadin.flow.server.WrappedSession;

/**
 * Component responsible for replicating HTTP session attributes to a
 * distributed storage.
 *
 * HTTP session attributes are serialized and deserialized by
 * {@link SessionSerializer}, using Java Serialization specifications.
 *
 * Transient fields of serialized object are inspected by a pluggable
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
 * pessimist approach</li>
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

    private static final long OPTIMISTIC_SERIALIZATION_TIMEOUT_MS = 30000;

    private final ExecutorService executorService = Executors
            .newFixedThreadPool(4, new SerializationThreadFactory());

    private final ConcurrentHashMap<String, Boolean> pending = new ConcurrentHashMap<>();

    private final BackendConnector backendConnector;

    private final TransientHandler handler;

    private final long optimisticSerializationTimeoutMs;

    private Predicate<Class<?>> injectableFilter = type -> true;

    /**
     * Creates a new {@link SessionSerializer}.
     *
     * @param backendConnector
     *            backend connector to store serialized data on distributed
     *            storage.
     * @param transientHandler
     *            handler to inspect and inject transient fields.
     */
    public SessionSerializer(BackendConnector backendConnector,
            TransientHandler transientHandler) {
        this.backendConnector = backendConnector;
        this.handler = transientHandler;
        optimisticSerializationTimeoutMs = OPTIMISTIC_SERIALIZATION_TIMEOUT_MS;
    }

    // Visible for test
    SessionSerializer(BackendConnector backendConnector,
            TransientHandler transientHandler,
            long optimisticSerializationTimeoutMs) {
        this.backendConnector = backendConnector;
        this.optimisticSerializationTimeoutMs = optimisticSerializationTimeoutMs;
        this.handler = transientHandler;
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
     * Serializes the given HTTP session and store data on a distributed
     * storage.
     *
     * @param session
     *            the HTTP session.
     */
    public void serialize(HttpSession session) {
        serialize(new WrappedHttpSession(session));
    }

    /**
     * Serializes the given Vaadin Wrapped session and store data on a
     * distributed storage.
     *
     * @param session
     *            the Vaadin Wrapped session.
     */
    public void serialize(WrappedSession session) {
        Map<String, Object> values = session.getAttributeNames().stream()
                .collect(Collectors.toMap(Function.identity(),
                        session::getAttribute));
        queueSerialization(session.getId(), values);
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
     * @throws ClassNotFoundException
     *             if class of a serialized object cannot be found.
     * @throws IOException
     *             any of the usual Input/Output related exceptions.
     */
    public void deserialize(SessionInfo sessionInfo, HttpSession session)
            throws ClassNotFoundException, IOException {
        Map<String, Object> values = doDeserialize(sessionInfo,
                session.getId());

        for (Entry<String, Object> entry : values.entrySet()) {
            session.setAttribute(entry.getKey(), entry.getValue());
        }
    }

    private void queueSerialization(String sessionId,
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
        backendConnector.markSerializationStarted(clusterKey);
        pending.put(sessionId, true);

        executorService.submit(() -> {
            Consumer<SessionInfo> whenSerialized = sessionInfo -> {
                backendConnector.sendSession(sessionInfo);
                backendConnector.markSerializationComplete(clusterKey);
            };

            handleSessionSerialization(sessionId, attributes, whenSerialized);
        });
    }

    private void handleSessionSerialization(String sessionId,
            Map<String, Object> attributes,
            Consumer<SessionInfo> whenSerialized) {
        long start = System.currentTimeMillis();
        long timeout = start + optimisticSerializationTimeoutMs;
        String clusterKey = getClusterKey(attributes);
        try {
            getLogger().debug(
                    "Optimistic serialization of session {} with distributed key {} started",
                    sessionId, clusterKey);
            while (System.currentTimeMillis() < timeout) {
                SessionInfo info = serializeOptimisticLocking(sessionId,
                        attributes);
                if (info != null) {
                    pending.remove(sessionId); // Is this a race condition?
                    getLogger().debug(
                            "Optimistic serialization of session {} with distributed key {} completed",
                            sessionId, clusterKey);
                    whenSerialized.accept(info);
                    return;
                }
            }
        } catch (IOException e) {
            getLogger().warn(
                    "Optimistic serialization of session {} with distributed key {} failed",
                    sessionId, clusterKey, e);
        }

        // Serializing using optimistic locking failed for a long time so be
        // pessimistic
        // and get it done
        pending.remove(sessionId);
        whenSerialized
                .accept(serializePessimisticLocking(sessionId, attributes));
    }

    private SessionInfo serializePessimisticLocking(String sessionId,
            Map<String, Object> attributes) {
        long start = System.currentTimeMillis();
        String clusterKey = getClusterKey(attributes);
        Set<ReentrantLock> locks = getLocks(attributes);
        for (ReentrantLock lock : locks) {
            lock.lock();
        }
        try {
            return doSerialize(sessionId, attributes);
        } catch (Exception e) {
            getLogger().error(
                    "An error occurred during pessimistic serialization of session {} with distributed key {} ",
                    sessionId, clusterKey, e);
        } finally {
            for (ReentrantLock lock : locks) {
                lock.unlock();
            }
            getLogger().debug(
                    "Pessimistic serialization of session {} with distributed key {} completed in {}ms",
                    sessionId, clusterKey, System.currentTimeMillis() - start);
        }
        return null;
    }

    private Set<ReentrantLock> getLocks(Map<String, Object> attributes) {
        Set<ReentrantLock> locks = new HashSet<>();
        for (String key : attributes.keySet()) {
            if (key.startsWith("com.vaadin.flow.server.VaadinSession")) {
                String serviceName = key.substring(
                        "com.vaadin.flow.server.VaadinSession".length() + 1);
                String lockKey = serviceName + ".lock";
                Object lockAttribute = attributes.get(lockKey);
                if (lockAttribute instanceof ReentrantLock) {
                    ReentrantLock lock = (ReentrantLock) lockAttribute;
                    locks.add(lock);
                }

            }
        }
        return locks;
    }

    private SessionInfo serializeOptimisticLocking(String sessionId,
            Map<String, Object> attributes) throws IOException {
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

            SessionInfo info = doSerialize(sessionId, attributes);

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
        } catch (NotSerializableException e) {
            getLogger().trace(
                    "Optimistic serialization of session {} with distributed key {} failed,"
                            + " some attribute is not serializable. Giving up immediately since the error is not recoverable",
                    sessionId, clusterKey, e);
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
            if (value instanceof VaadinSession) {
                VaadinSession s = (VaadinSession) value;
                try {
                    for (UI ui : s.getUIs()) {
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
            if (entry.getValue() instanceof VaadinSession) {
                VaadinSession session = (VaadinSession) entry.getValue();
                latestLock = Math.max(latestLock, session.getLastLocked());
            }
        }
        return latestLock;
    }

    private long findNewestUnlockTime(Map<String, Object> attributes) {
        long latestUnlock = 0L;
        for (Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() instanceof VaadinSession) {
                VaadinSession session = (VaadinSession) entry.getValue();
                latestUnlock = Math.max(latestUnlock,
                        session.getLastUnlocked());
            }
        }
        return latestUnlock;
    }

    private SessionInfo doSerialize(String sessionId,
            Map<String, Object> attributes) throws Exception {
        long start = System.currentTimeMillis();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TransientInjectableObjectOutputStream outStream = new TransientInjectableObjectOutputStream(
                out, handler, injectableFilter)) {
            outStream.writeWithTransients(attributes);
        }

        SessionInfo info = new SessionInfo(getClusterKey(attributes),
                out.toByteArray());

        getLogger().debug(
                "Serialization of attributes {} for session {} with distributed key {} completed in {}ms",
                attributes.keySet(), sessionId, info.getClusterKey(),
                System.currentTimeMillis() - start);
        return info;
    }

    private String getClusterKey(Map<String, Object> attributes) {
        return (String) attributes.get(CurrentKey.COOKIE_NAME);
    }

    private Map<String, Object> doDeserialize(SessionInfo sessionInfo,
            String sessionId) throws IOException, ClassNotFoundException {
        byte[] data = sessionInfo.getData();
        long start = System.currentTimeMillis();

        // Is this needed?
        ClassLoader contextLoader = Thread.currentThread()
                .getContextClassLoader();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        Map<String, Object> attributes;
        try (TransientInjectableObjectInputStream inStream = new TransientInjectableObjectInputStream(
                in, handler)) {
            attributes = inStream.readWithTransients();
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
    }

    private static class SerializationThreadFactory implements ThreadFactory {

        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "sessionSerializer-worker-"
                    + threadNumber.getAndIncrement());
        }

    }
}
