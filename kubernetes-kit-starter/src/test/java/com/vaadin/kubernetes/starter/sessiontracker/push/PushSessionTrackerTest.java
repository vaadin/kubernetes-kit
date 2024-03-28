package com.vaadin.kubernetes.starter.sessiontracker.push;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.UUID;

import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.kubernetes.starter.sessiontracker.CurrentKey;
import com.vaadin.kubernetes.starter.sessiontracker.SessionSerializer;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushSessionTrackerTest {

    private HttpSession httpSession;
    private HttpServletRequest servletRequest;
    private SessionSerializer sessionSerializer;
    private PushSessionTracker sessionTracker;

    @BeforeEach
    void setup() {
        httpSession = mock(HttpSession.class);
        servletRequest = mock(HttpServletRequest.class);
        sessionSerializer = mock(SessionSerializer.class);
        sessionTracker = new PushSessionTracker(sessionSerializer);
        sessionTracker.setActiveSessionChecker(id -> true);
    }

    AtmosphereResource createResource(String clusterKey) {
        AtmosphereResource resource = mock(AtmosphereResource.class);
        when(resource.uuid()).thenReturn(UUID.randomUUID().toString());

        AtmosphereFramework atmosphere = new AtmosphereFramework();
        when(resource.getAtmosphereConfig())
                .thenReturn(atmosphere.getAtmosphereConfig());

        if (clusterKey != null) {
            atmosphere.getAtmosphereConfig().sessionFactory()
                    .getSession(resource)
                    .setAttribute(CurrentKey.COOKIE_NAME, clusterKey);
        }

        when(resource.session(anyBoolean())).thenReturn(httpSession);
        AtmosphereRequest request = mock(AtmosphereRequest.class);
        when(resource.getRequest()).thenReturn(request);
        when(request.wrappedRequest()).thenReturn(servletRequest);
        return resource;
    }

    @Test
    void onConnect_clusterKeyFromCookie_storeClusterKeyOnResourceSession() {
        AtmosphereResource resource = createResource(null);
        String clusterKey = UUID.randomUUID().toString();
        when(servletRequest.getCookies()).thenReturn(new Cookie[] {
                new Cookie(CurrentKey.COOKIE_NAME, clusterKey) });

        sessionTracker.onConnect(resource);

        AtmosphereResourceSession resourceSession = resource
                .getAtmosphereConfig().sessionFactory()
                .getSession(resource, false);
        Assertions.assertEquals(clusterKey,
                resourceSession.getAttribute(CurrentKey.COOKIE_NAME));
    }

    @Test
    void onConnect_clusterKeyFromHTTPSession_storeClusterKeyOnResourceSession() {
        AtmosphereResource resource = createResource(null);
        String clusterKey = UUID.randomUUID().toString();
        when(httpSession.getAttribute(CurrentKey.COOKIE_NAME))
                .thenReturn(clusterKey);

        sessionTracker.onConnect(resource);

        AtmosphereResourceSession resourceSession = resource
                .getAtmosphereConfig().sessionFactory()
                .getSession(resource, false);
        Assertions.assertEquals(clusterKey,
                resourceSession.getAttribute(CurrentKey.COOKIE_NAME));
    }

    @Test
    void onMessageSent_nullSession_sessionIsNotSerialized() {
        AtmosphereResource resource = mock(AtmosphereResource.class);
        when(resource.session(anyBoolean())).thenReturn(null);

        sessionTracker.onMessageSent(resource);

        verify(sessionSerializer, never()).serialize(any(HttpSession.class));
    }

    @Test
    void onMessageSent_missingClusterKey_sessionIsNotSerialized() {
        AtmosphereResource resource = createResource(null);

        sessionTracker.onMessageSent(resource);

        verify(sessionSerializer, never()).serialize(any(HttpSession.class));
    }

    @Test
    void onMessageSent_inactiveSession_sessionIsNotSerialized() {
        AtmosphereResource resource = createResource(
                UUID.randomUUID().toString());

        sessionTracker.setActiveSessionChecker(id -> false);
        sessionTracker.onMessageSent(resource);

        verify(sessionSerializer, never()).serialize(any(HttpSession.class));
    }

    @Test
    void onMessageSent_sessionIsSerialized() {
        String clusterKey = UUID.randomUUID().toString();
        AtmosphereResource resource = createResource(clusterKey);

        sessionTracker.onMessageSent(resource);

        verify(sessionSerializer).serialize(eq(httpSession));
        assertNull(CurrentKey.get());
    }
}
