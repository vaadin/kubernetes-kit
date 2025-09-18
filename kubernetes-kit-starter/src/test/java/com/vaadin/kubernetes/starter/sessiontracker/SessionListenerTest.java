package com.vaadin.kubernetes.starter.sessiontracker;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.vaadin.kubernetes.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionInfo;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SessionListenerTest {

    @AfterEach
    void cleanUp() {
        CurrentKey.clear();
    }

    @Test
    void sessionCreated_nullClusterKey_sessionIsNotDeserialized() {
        BackendConnector backendConnector = mock(BackendConnector.class);
        SessionSerializer sessionSerializer = mock(SessionSerializer.class);
        HttpSessionEvent sessionEvent = createSessionEvent();

        SessionListener listener = new SessionListener(backendConnector,
                sessionSerializer);
        listener.sessionCreated(sessionEvent);

        verify(sessionEvent).getSession();
        verify(backendConnector, never()).getSession(any());
        try {
            verify(sessionSerializer, never()).deserialize(any(), any());
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    void sessionCreated_nullSessionInfo_sessionIsNotDeserialized() {
        String clusterKey = CurrentKey.COOKIE_NAME;
        CurrentKey.set(clusterKey);

        BackendConnector backendConnector = mock(BackendConnector.class);
        SessionSerializer sessionSerializer = mock(SessionSerializer.class);

        HttpSessionEvent sessionEvent = createSessionEvent();

        SessionListener listener = new SessionListener(backendConnector,
                sessionSerializer);
        listener.sessionCreated(sessionEvent);

        verify(sessionEvent).getSession();
        verify(backendConnector).getSession(eq(clusterKey));
        try {
            verify(sessionSerializer, never()).deserialize(any(), any());
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    void sessionCreated_sessionIsDeserialized() {
        String clusterKey = CurrentKey.COOKIE_NAME;
        CurrentKey.set(clusterKey);

        BackendConnector backendConnector = mock(BackendConnector.class);
        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(backendConnector.getSession(any())).thenReturn(sessionInfo);

        SessionSerializer sessionSerializer = mock(SessionSerializer.class);

        AtomicReference<HttpSession> sessionHolder = new AtomicReference<>();
        HttpSessionEvent sessionEvent = createSessionEvent(sessionHolder::set);

        SessionListener listener = new SessionListener(backendConnector,
                sessionSerializer);
        listener.sessionCreated(sessionEvent);

        verify(sessionEvent).getSession();
        verify(backendConnector).getSession(eq(clusterKey));
        try {
            verify(sessionSerializer).deserialize(eq(sessionInfo),
                    eq(sessionHolder.get()));
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    void sessionDestroyed_replicateSessionIsDeleted() {
        BackendConnector backendConnector = mock(BackendConnector.class);
        SessionSerializer sessionSerializer = mock(SessionSerializer.class);

        HttpSessionEvent sessionEvent = createSessionEvent(
                session -> when(session.getAttribute(CurrentKey.COOKIE_NAME))
                        .thenReturn("clusterKey"));

        SessionListener listener = new SessionListener(backendConnector,
                sessionSerializer);
        listener.sessionDestroyed(sessionEvent);

        verify(sessionEvent).getSession();
        verify(backendConnector).deleteSession(any());
    }

    @Test
    void sessionDestroyed_nullClusterKey_replicateSessionIsNotDeleted() {
        BackendConnector backendConnector = mock(BackendConnector.class);
        SessionSerializer sessionSerializer = mock(SessionSerializer.class);
        HttpSessionEvent sessionEvent = createSessionEvent();

        SessionListener listener = new SessionListener(backendConnector,
                sessionSerializer);
        listener.sessionDestroyed(sessionEvent);

        verify(sessionEvent).getSession();
        verify(backendConnector, never()).deleteSession(any());
    }

    @Test
    void activeSessionChecker_trackSessionLifecycle() {
        BackendConnector backendConnector = mock(BackendConnector.class);
        SessionSerializer sessionSerializer = mock(SessionSerializer.class);
        SessionListener listener = new SessionListener(backendConnector,
                sessionSerializer);

        Predicate<String> checker = listener.activeSessionChecker();

        HttpSessionEvent sessionEvent = createSessionEvent();
        listener.sessionCreated(sessionEvent);
        Assertions.assertTrue(checker.test(sessionEvent.getSession().getId()),
                "HTTP Session should be active");

        listener.sessionDestroyed(sessionEvent);
        Assertions.assertFalse(checker.test(sessionEvent.getSession().getId()),
                "HTTP Session should not be active");

    }

    @Test
    void sessionListenerStopped_sessionNotCreatedOrDestroyed() {
        BackendConnector backendConnector = mock(BackendConnector.class);
        SessionSerializer sessionSerializer = mock(SessionSerializer.class);
        HttpSessionEvent sessionEvent = createSessionEvent();
        SessionListener listener = new SessionListener(backendConnector,
                sessionSerializer);
        listener.stop();

        listener.sessionCreated(sessionEvent);
        verify(sessionEvent, never()).getSession();

        listener.sessionDestroyed(sessionEvent);
        verify(sessionEvent, never()).getSession();
    }

    private static HttpSessionEvent createSessionEvent() {
        return createSessionEvent(session -> {
        });
    }

    private static HttpSessionEvent createSessionEvent(
            Consumer<HttpSession> customizer) {
        HttpSessionEvent sessionEvent = mock(HttpSessionEvent.class);
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        when(sessionEvent.getSession()).thenReturn(session);
        customizer.accept(session);
        return sessionEvent;
    }

}
