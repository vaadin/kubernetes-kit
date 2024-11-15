package com.vaadin.kubernetes.starter.sessiontracker;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.shared.ApplicationConstants;
import com.vaadin.kubernetes.starter.KubernetesKitProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({ MockitoExtension.class })
class SessionTrackerFilterTest {

    SessionSerializer serializer = mock(SessionSerializer.class);
    SessionTrackerFilter filter = new SessionTrackerFilter(serializer,
            new KubernetesKitProperties());

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    FilterChain filterChain = mock(FilterChain.class);

    Cookie cookie = new Cookie(CurrentKey.COOKIE_NAME,
            UUID.randomUUID().toString());

    @Captor
    private ArgumentCaptor<Consumer<Cookie>> cookieConsumerArgumentCaptor;

    @AfterEach
    void assertFilterChainIsAlwaysExecuted()
            throws ServletException, IOException {
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validHttpSession_UIDLRequest_sessionSerialized() throws Exception {
        setupCookie();
        when(request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER))
                .thenReturn(HandlerHelper.RequestType.UIDL.getIdentifier());
        when(request.isRequestedSessionIdValid()).thenReturn(true);
        MockHttpSession httpSession = setupHttpSession();
        filter.doFilter(request, response, filterChain);

        verify(serializer).serialize(httpSession);
        assertThat(httpSession.getAttribute(CurrentKey.COOKIE_NAME))
                .isEqualTo(cookie.getValue());
    }

    @Test
    void vaadinRequest_notUIDL_sessionSerialized() throws Exception {
        setupCookie();
        when(request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER))
                .thenReturn(HandlerHelper.RequestType.INIT.getIdentifier());
        when(request.isRequestedSessionIdValid()).thenReturn(true);
        MockHttpSession httpSession = setupHttpSession();
        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(serializer);
        assertThat(httpSession.getAttribute(CurrentKey.COOKIE_NAME))
                .isEqualTo(cookie.getValue());
    }

    @Test
    void nullHttpSession_notUIDLRequest_sessionSerialized() throws Exception {
        setupCookie();
        when(request.isRequestedSessionIdValid()).thenReturn(true);
        filter.doFilter(request, response, filterChain);
        verifyNoInteractions(serializer);
    }

    @Test
    void validHttpSession_notUIDLRequest_sessionSerialized() throws Exception {
        setupCookie();
        when(request.isRequestedSessionIdValid()).thenReturn(true);
        MockHttpSession httpSession = setupHttpSession();
        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(serializer);
        assertThat(httpSession.getAttribute(CurrentKey.COOKIE_NAME))
                .isEqualTo(cookie.getValue());
    }

    @Test
    void notExistingSession_trackedCookiePresent_sessionInitialized()
            throws Exception {
        setupCookie();
        MockHttpSession httpSession = new MockHttpSession();
        when(request.getSession(false)).thenReturn(null, httpSession);
        when(request.getSession(true)).thenReturn(httpSession);
        when(request.isRequestedSessionIdValid()).thenReturn(true);
        filter.doFilter(request, response, filterChain);

        assertThat(httpSession.getAttribute(CurrentKey.COOKIE_NAME))
                .isEqualTo(cookie.getValue());
    }

    @Test
    void invalidatedHttpSession_UIDLRequest_sessionNotSerialized()
            throws Exception {
        setupCookie();
        when(request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER))
                .thenReturn(HandlerHelper.RequestType.UIDL.getIdentifier());
        when(request.isRequestedSessionIdValid()).thenReturn(false);
        MockHttpSession httpSession = setupHttpSession();
        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(serializer);
        assertThat(httpSession.getAttribute(CurrentKey.COOKIE_NAME))
                .isEqualTo(cookie.getValue());
    }

    @Test
    void validHttpSession_cookieConsumer_configuresCookie() throws Exception {
        Cookie cookie = new Cookie("clusterKey", "value");
        setupHttpSession();
        when(request.getContextPath()).thenReturn("contextpath");

        try (MockedStatic<SessionTrackerCookie> mockedStatic = mockStatic(
                SessionTrackerCookie.class)) {
            filter.doFilter(request, response, filterChain);
            mockedStatic.verify(() -> SessionTrackerCookie.setIfNeeded(any(),
                    any(), any(), anyString(),
                    cookieConsumerArgumentCaptor.capture()));
            Consumer<Cookie> cookieConsumer = cookieConsumerArgumentCaptor
                    .getValue();
            cookieConsumer.accept(cookie);
        }

        assertTrue(cookie.isHttpOnly());
        assertEquals("contextpath", cookie.getPath());
        assertEquals("Strict", cookie.getAttribute("SameSite"));
    }

    private MockHttpSession setupHttpSession() {
        MockHttpSession httpSession = new MockHttpSession();
        when(request.getSession(false)).thenReturn(httpSession);
        return httpSession;
    }

    private void setupCookie() {
        when(request.getCookies()).thenReturn(new Cookie[] { cookie });
    }
}
