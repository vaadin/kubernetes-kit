package com.vaadin.azure.starter.sessiontracker;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;

import org.junit.jupiter.api.Test;

import com.vaadin.azure.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.azure.starter.sessiontracker.backend.SessionInfo;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SessionListenerTest {
    @Test
    void sessionCreated_nullClusterKey_sessionIsNotDeserialized() {
        BackendConnector backendConnector = mock(BackendConnector.class);
        SessionSerializer sessionSerializer = mock(SessionSerializer.class);
        HttpSessionEvent sessionEvent = mock(HttpSessionEvent.class);
        HttpSession session = mock(HttpSession.class);
        when(sessionEvent.getSession()).thenReturn(session);

        SessionListener listener = new SessionListener(backendConnector,
                sessionSerializer);
        listener.sessionCreated(sessionEvent);

        verify(sessionEvent).getSession();
        verify(backendConnector, never()).getSession(any());
        try {
            verify(sessionSerializer, never()).deserialize(any(), any());
        } catch(Exception e) {
            fail();
        }
    }

    @Test
    void sessionCreated_nullSessionInfo_sessionIsNotDeserialized() {
        String clusterKey = CurrentKey.COOKIE_NAME;
        CurrentKey.set(clusterKey);

        BackendConnector backendConnector = mock(BackendConnector.class);
        SessionSerializer sessionSerializer = mock(SessionSerializer.class);

        HttpSessionEvent sessionEvent = mock(HttpSessionEvent.class);
        HttpSession session = mock(HttpSession.class);
        when(sessionEvent.getSession()).thenReturn(session);

        SessionListener listener = new SessionListener(backendConnector,
                sessionSerializer);
        listener.sessionCreated(sessionEvent);

        verify(sessionEvent).getSession();
        verify(backendConnector).getSession(eq(clusterKey));
        try {
            verify(sessionSerializer, never()).deserialize(any(), any());
        } catch(Exception e) {
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

        HttpSessionEvent sessionEvent = mock(HttpSessionEvent.class);
        HttpSession session = mock(HttpSession.class);
        when(sessionEvent.getSession()).thenReturn(session);

        SessionListener listener = new SessionListener(backendConnector,
                sessionSerializer);
        listener.sessionCreated(sessionEvent);

        verify(sessionEvent).getSession();
        verify(backendConnector).getSession(eq(clusterKey));
        try {
            verify(sessionSerializer).deserialize(eq(sessionInfo), eq(session));
        } catch(Exception e) {
            fail();
        }
    }
}
