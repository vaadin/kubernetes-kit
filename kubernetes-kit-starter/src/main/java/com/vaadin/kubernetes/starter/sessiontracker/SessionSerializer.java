package com.vaadin.kubernetes.starter.sessiontracker;

import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
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

import com.vaadin.kubernetes.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionInfo;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientHandler;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientInjectableObjectInputStream;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientInjectableObjectOutputStream;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedHttpSession;
import com.vaadin.flow.server.WrappedSession;

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

    public void serialize(HttpSession session) {
        serialize(new WrappedHttpSession(session));
    }

    public void serialize(WrappedSession session) {
        Map<String, Object> values = session.getAttributeNames().stream()
                .collect(Collectors.toMap(Function.identity(),
                        session::getAttribute));
        queueSerialization(session.getId(), values);
    }

    private void queueSerialization(String sessionId,
            Map<String, Object> attributes) {
        if (pending.containsKey(sessionId)) {
            // This session will be serialized again soon enough
            getLogger().info(
                    "Ignoring serialization request for {} as the session is already being serialized ",
                    sessionId);
            return;
        }
        getLogger().info("Starting serialization of session {}",
                getClusterKey(attributes));
        String clusterKey = getClusterKey(attributes);
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
        try {
            getLogger().info("Optimistic serialization of session {} started",
                    getClusterKey(attributes));
            while (System.currentTimeMillis() < timeout) {
                SessionInfo info = serializeOptimisticLocking(sessionId,
                        attributes);
                if (info != null) {
                    pending.remove(sessionId); // Is this a race condition?
                    getLogger().warn(
                            "Optimistic serialization of session {} completed",
                            getClusterKey(attributes));
                    whenSerialized.accept(info);
                    return;
                }
            }
        } catch (IOException e) {
            getLogger().warn("Optimistic serialization failed", e);
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
        Set<ReentrantLock> locks = getLocks(attributes);
        for (ReentrantLock lock : locks) {
            lock.lock();
        }
        try {
            return doSerialize(sessionId, attributes);
        } catch (Exception e) {
            getLogger().error(
                    "An error occured during pessimistic serialization", e);
        } finally {
            for (ReentrantLock lock : locks) {
                lock.unlock();
            }
            getLogger().info("serializePessimisticLocking done in {}ms",
                    System.currentTimeMillis() - start);
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
        try {
            long latestLockTime = findNewestLockTime(attributes);
            long latestUnlockTime = findNewestUnlockTime(attributes);

            if (latestLockTime > latestUnlockTime) {
                // The session is locked
                getLogger().trace(
                        "Optimistic serialization failed, session is locked. Will retry");
                return null;
            }

            SessionInfo info = doSerialize(sessionId, attributes);

            long latestUnlockTimeCheck = findNewestUnlockTime(attributes);

            if (latestUnlockTime != latestUnlockTimeCheck) {
                // Somebody modified the session during serialization and the
                // result cannot be
                // used
                getLogger().trace(
                        "Optimistic serialization failed, somebody modified the session during serialization ({} != {}). Will retry",
                        latestUnlockTime, latestUnlockTimeCheck);
                return null;
            }
            logSessionDebugInfo("Serialized session", attributes);
            return info;
        } catch (NotSerializableException e) {
            getLogger().trace(
                    "Optimistic serialization failed, some attribute is not serializable. Giving up immediately since the error is not recoverable",
                    e);
            throw e;
        } catch (Exception e) {
            getLogger().trace(
                    "Optimistic serialization failed, a problem occured during serialization. Will retry",
                    e);
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
                        info.append("[UI " + ui.getUIId()
                                + ", last client message: "
                                + ui.getInternals()
                                        .getLastProcessedClientToServerId()
                                + ", server sync id: "
                                + ui.getInternals().getServerSyncId() + "]");
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
        getLogger().info(prefix + " UIs: " + info);
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

        getLogger().info("doSerialize session with keys {} in {}ms",
                attributes.keySet(), System.currentTimeMillis() - start);
        return info;
    }

    private String getClusterKey(Map<String, Object> attributes) {
        return (String) attributes.get(CurrentKey.COOKIE_NAME);
    }

    private Map<String, Object> doDeserialize(byte[] data)
            throws IOException, ClassNotFoundException {
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

        getLogger().info("doDeserialize session with keys {} in {}ms",
                attributes.keySet(), System.currentTimeMillis() - start);

        return attributes;
    }

    public void deserialize(SessionInfo sessionInfo, HttpSession session)
            throws ClassNotFoundException, IOException {
        Map<String, Object> values = doDeserialize(sessionInfo.getData());

        for (Entry<String, Object> entry : values.entrySet()) {
            session.setAttribute(entry.getKey(), entry.getValue());
        }
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(SessionSerializer.class);
    }

    public void waitForSerialization() {
        while (!pending.isEmpty()) {
            getLogger().info("Waiting for " + pending.size()
                    + " sessions to be serialized");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
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

    @FunctionalInterface
    public interface ObjectInputStreamFactory {

        ObjectInputStream newInstance(InputStream inputStream)
                throws IOException;

        ObjectInputStreamFactory DEFAULT = ObjectInputStream::new;
    }
}