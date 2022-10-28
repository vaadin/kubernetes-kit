package com.vaadin.azure.starter.sessiontracker;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.vaadin.azure.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.azure.starter.sessiontracker.backend.SessionInfo;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedHttpSession;
import com.vaadin.flow.server.startup.ApplicationConfiguration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class SessionSerializerTest {

    public static final int TEST_OPTIMISTIC_SERIALIZATION_TIMEOUT_MS = 3000;
    BackendConnector connector;
    SessionSerializer serializer;
    private MockVaadinSession vaadinSession;
    private HttpSession httpSession;
    private String clusterSID;
    private MockVaadinService vaadinService;

    @BeforeEach
    void setUp() {

        System.setProperty("sun.io.serialization.extendedDebugInfo", "true");
        connector = mock(BackendConnector.class);
        serializer = new SessionSerializer(connector,
                TEST_OPTIMISTIC_SERIALIZATION_TIMEOUT_MS);

        clusterSID = UUID.randomUUID().toString();
        httpSession = newHttpSession(clusterSID);

        vaadinService = new MockVaadinService();
        vaadinSession = vaadinService.newMockSession(httpSession);
    }

    private static HttpSession newHttpSession(String clusterSID) {
        HttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute(CurrentKey.COOKIE_NAME, clusterSID);
        return httpSession;
    }

    @Test
    void serialize_optimisticLocking_sessionNotLocked() {
        AtomicBoolean serializationCompleted = new AtomicBoolean();
        doAnswer(i -> serializationCompleted.getAndSet(true)).when(connector)
                .markSerializationComplete(clusterSID);

        vaadinSession.setLockTimestamps(10, 20);

        serializer.serialize(httpSession);
        verify(connector).markSerializationStarted(clusterSID);

        await().atMost(1000, MILLISECONDS).untilTrue(serializationCompleted);
        verify(connector).sendSession(notNull());
    }

    @Test
    void serialize_optimisticLocking_sessionLocked() {
        AtomicBoolean serializationCompleted = new AtomicBoolean();
        doAnswer(i -> serializationCompleted.getAndSet(true)).when(connector)
                .markSerializationComplete(clusterSID);

        // vaadin session currently locked
        vaadinSession.setLockTimestamps(30, 20);

        serializer.serialize(httpSession);
        verify(connector).markSerializationStarted(clusterSID);

        await().during(100, MILLISECONDS).untilFalse(serializationCompleted);

        // vaadin session unlocked
        vaadinSession.setLockTimestamps(30, 40);

        await().atMost(1000, MILLISECONDS).untilTrue(serializationCompleted);
        verify(connector).sendSession(notNull());
    }

    @Test
    void serialize_optimisticLocking_sessionChanged() {
        AtomicBoolean serializationCompleted = new AtomicBoolean();
        doAnswer(i -> serializationCompleted.getAndSet(true)).when(connector)
                .markSerializationComplete(clusterSID);

        vaadinSession.setLockTimestamps(10, 20);
        // delay session serialization completion, so we can simulate another
        // request locking the Vaadin session during serialization
        httpSession.setAttribute("DELAY", new SerializationDelay(300));

        serializer.serialize(httpSession);
        verify(connector).markSerializationStarted(clusterSID);

        await().during(100, MILLISECONDS).untilFalse(serializationCompleted);
        // another request is locking the session
        vaadinSession.setLockTimestamps(30, 40);
        Assertions.assertFalse(serializationCompleted.get(),
                "Expecting serialization to be in progress");

        await().atMost(1000, MILLISECONDS).untilTrue(serializationCompleted);
        // Serialization should not be sent the backend
        verify(connector).sendSession(isNotNull());
    }

    @Test
    void serialize_pendingSerialization_skip() {
        AtomicInteger serializationsCompleted = new AtomicInteger();
        AtomicInteger serializationsStarted = new AtomicInteger();
        doAnswer(i -> serializationsCompleted.incrementAndGet()).when(connector)
                .markSerializationComplete(clusterSID);
        doAnswer(i -> serializationsStarted.incrementAndGet()).when(connector)
                .markSerializationStarted(clusterSID);

        vaadinSession.setLockTimestamps(10, 20);
        // delay session serialization completion, so we can verify that a
        // concurrent serialization request is skipped
        httpSession.setAttribute("DELAY", new SerializationDelay(500));

        serializer.serialize(httpSession);

        await().atMost(100, MILLISECONDS)
                .until(() -> serializationsStarted.get() == 1);

        // try serialize again, request should be skipped
        serializer.serialize(httpSession);

        await().atMost(1000, MILLISECONDS)
                .until(() -> serializationsStarted.get() == 1
                        && serializationsCompleted.get() == 1);

        verify(connector, times(1)).markSerializationStarted(clusterSID);
        verify(connector, times(1)).sendSession(notNull());
        verify(connector, times(1)).markSerializationComplete(clusterSID);
    }

    @Test
    void serialize_pessimisticLocking() {
        AtomicBoolean serializationCompleted = new AtomicBoolean();
        doAnswer(i -> serializationCompleted.getAndSet(true)).when(connector)
                .markSerializationComplete(clusterSID);

        // vaadin session currently locked
        vaadinSession.lock();
        vaadinSession.setLockTimestamps(30, 20);

        serializer.serialize(httpSession);
        verify(connector).markSerializationStarted(clusterSID);

        await().during(TEST_OPTIMISTIC_SERIALIZATION_TIMEOUT_MS + 100,
                MILLISECONDS).untilFalse(serializationCompleted);

        // vaadin session unlocked
        vaadinSession.unlock();
        vaadinSession.setLockTimestamps(30, 40);

        await().atMost(200, MILLISECONDS).untilTrue(serializationCompleted);
        verify(connector).sendSession(notNull());
    }

    @Test
    void serialize_differentSessions_processedConcurrently() {

        List<String> started = new ArrayList<>();
        List<String> completed = new ArrayList<>();
        List<SessionInfo> infoList = new ArrayList<>();

        doAnswer(i -> started.add(i.getArgument(0))).when(connector)
                .markSerializationStarted(anyString());
        doAnswer(i -> infoList.add(i.getArgument(0))).when(connector)
                .sendSession(any());
        doAnswer(i -> completed.add(i.getArgument(0))).when(connector)
                .markSerializationComplete(anyString());

        String sid1 = UUID.randomUUID().toString();
        HttpSession session1 = newHttpSession(sid1);
        session1.setAttribute("DELAY", new SerializationDelay(200));
        vaadinService.newMockSession(session1);

        String sid2 = UUID.randomUUID().toString();
        HttpSession session2 = newHttpSession(sid2);
        session2.setAttribute("DELAY", new SerializationDelay(100));
        vaadinService.newMockSession(session2);

        serializer.serialize(session1);
        serializer.serialize(session2);

        await().atMost(1000, MILLISECONDS).until(() -> completed.size() == 2);

        Assertions.assertIterableEquals(List.of(sid1, sid2), started,
                "Started serializations");
        Assertions
                .assertIterableEquals(List.of(sid2, sid1),
                        infoList.stream().map(SessionInfo::getClusterKey)
                                .collect(Collectors.toList()),
                        "Started completed");
        Assertions.assertIterableEquals(List.of(sid2, sid1), completed,
                "Started completed");

        verify(connector, times(2)).markSerializationStarted(anyString());
        verify(connector, times(2)).sendSession(notNull());
        verify(connector, times(2)).markSerializationComplete(anyString());

    }

    private static ConditionFactory await() {
        return Awaitility.with().pollInterval(20, MILLISECONDS);
    }

    private static class MockVaadinSession extends VaadinSession {

        long lastLocked;
        long lastUnlocked;

        /**
         * Creates a new VaadinSession tied to a VaadinService.
         *
         * @param service
         *            the Vaadin service for the new session
         */
        public MockVaadinSession(VaadinService service) {
            super(service);
        }

        @Override
        public long getLastLocked() {
            return lastLocked;
        }

        @Override
        public long getLastUnlocked() {
            return lastUnlocked;
        }

        void setLockTimestamps(long lastLocked, long lastUnlocked) {
            this.lastLocked = lastLocked;
            this.lastUnlocked = lastUnlocked;
        }
    }

    private static class MockVaadinService extends VaadinServletService {

        @Override
        protected VaadinContext constructVaadinContext() {
            ApplicationConfiguration applicationConfiguration = mock(
                    ApplicationConfiguration.class);

            VaadinContext vaadinContext = mock(VaadinContext.class);
            when(vaadinContext.getAttribute(eq(ApplicationConfiguration.class),
                    any())).thenReturn(applicationConfiguration);
            when(applicationConfiguration.isProductionMode()).thenReturn(false);
            when(applicationConfiguration
                    .isDevModeSessionSerializationEnabled()).thenReturn(true);
            return vaadinContext;
        }

        @Override
        public String getServiceName() {
            return getClass().getName();
        }

        MockVaadinSession newMockSession(HttpSession httpSession) {
            MockVaadinSession session = new MockVaadinSession(this);
            WrappedHttpSession wrappedSession = new WrappedHttpSession(
                    httpSession);
            Lock lock = lockSession(wrappedSession);
            try {
                session.refreshTransients(wrappedSession, this);
                storeSession(session, wrappedSession);

                DeploymentConfiguration deploymentConfiguration = mock(
                        DeploymentConfiguration.class,
                        withSettings().serializable());
                when(deploymentConfiguration.isProductionMode())
                        .thenReturn(false);
                session.setConfiguration(deploymentConfiguration);
            } finally {
                lock.unlock();
            }
            return session;
        }
    }

    // A serializable class that suspends the serialization process for a given
    // amount of time
    private static class SerializationDelay implements Serializable {
        private final long delayMillis;

        public SerializationDelay(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        private void writeObject(java.io.ObjectOutputStream stream)
                throws IOException {
            stream.defaultWriteObject();
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
