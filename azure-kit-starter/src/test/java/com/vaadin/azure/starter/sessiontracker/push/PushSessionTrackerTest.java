package com.vaadin.azure.starter.sessiontracker.push;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.UUID;

import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.junit.jupiter.api.Test;

import com.vaadin.azure.starter.sessiontracker.CurrentKey;
import com.vaadin.azure.starter.sessiontracker.SessionSerializer;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushSessionTrackerTest {
    @Test
    void onMessageSent_nullSession_sessionIsNotSerialized() {
        SessionSerializer sessionSerializer = mock(SessionSerializer.class);

        AtmosphereResource resource = mock(AtmosphereResource.class);
        when(resource.session(anyBoolean())).thenReturn(null);

        PushSessionTracker sessionTracker = new PushSessionTracker(
                sessionSerializer);
        sessionTracker.onMessageSent(resource);

        verify(sessionSerializer, never()).serialize(any(HttpSession.class));
    }

    @Test
    void onMessageSent_invalidatedSession_sessionIsNotSerialized() {
        SessionSerializer sessionSerializer = mock(SessionSerializer.class);

        AtmosphereResource resource = mock(AtmosphereResource.class);
        HttpSession session = mock(HttpSession.class);
        when(resource.session(anyBoolean())).thenReturn(session);
        AtmosphereRequest request = mock(AtmosphereRequest.class);
        when(resource.getRequest()).thenReturn(request);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(request.wrappedRequest()).thenReturn(servletRequest);
        when(servletRequest.isRequestedSessionIdValid()).thenReturn(false);

        PushSessionTracker sessionTracker = new PushSessionTracker(
                sessionSerializer);
        sessionTracker.onMessageSent(resource);

        verify(sessionSerializer, never()).serialize(any(HttpSession.class));
    }

    @Test
    void onMessageSent_sessionIsSerialized() {
        SessionSerializer sessionSerializer = mock(SessionSerializer.class);

        AtmosphereResource resource = mock(AtmosphereResource.class);
        HttpSession session = mock(HttpSession.class);
        when(resource.session(anyBoolean())).thenReturn(session);
        AtmosphereRequest request = mock(AtmosphereRequest.class);
        when(resource.getRequest()).thenReturn(request);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(request.wrappedRequest()).thenReturn(servletRequest);
        when(servletRequest.isRequestedSessionIdValid()).thenReturn(true);
        String clusterKey = UUID.randomUUID().toString();
        when(servletRequest.getCookies()).thenReturn(new Cookie[] {
                new Cookie(CurrentKey.COOKIE_NAME, clusterKey) });

        PushSessionTracker sessionTracker = new PushSessionTracker(
                sessionSerializer);
        sessionTracker.onMessageSent(resource);

        verify(sessionSerializer).serialize(eq(session));
        assertNull(CurrentKey.get());
    }
}
