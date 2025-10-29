package com.vaadin.kubernetes.starter.sessiontracker;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpSession;

import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.shared.ApplicationConstants;
import com.vaadin.kubernetes.starter.KubernetesKitProperties;
import com.vaadin.kubernetes.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({ MockitoExtension.class })
class SessionTrackerFilterTest {

    SessionSerializer serializer = mock(SessionSerializer.class);
    BackendConnector backendConnector = Mockito.mock(BackendConnector.class);
    SessionListener sessionListener = spy(
            new SessionListener(backendConnector, serializer));
    SessionTrackerFilter filter = new SessionTrackerFilter(serializer,
            new KubernetesKitProperties(), sessionListener);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    FilterChain filterChain = mock(FilterChain.class);

    Cookie cookie = new Cookie(CurrentKey.COOKIE_NAME,
            UUID.randomUUID().toString());

    @Captor
    private ArgumentCaptor<Consumer<Cookie>> cookieConsumerArgumentCaptor;

    @BeforeEach
    void mockBackendConnector() {
        when(backendConnector.getSession(any()))
                .thenReturn(new SessionInfo("TEST", new byte[0]));
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
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void vaadinRequest_notUIDL_sessionNotSerialized() throws Exception {
        setupCookie();
        when(request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER))
                .thenReturn(HandlerHelper.RequestType.INIT.getIdentifier());
        when(request.isRequestedSessionIdValid()).thenReturn(true);
        MockHttpSession httpSession = setupHttpSession();
        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(serializer);
        assertThat(httpSession.getAttribute(CurrentKey.COOKIE_NAME))
                .isEqualTo(cookie.getValue());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nonVaadinRequest_notUIDLRequest_sessionNotSerialized()
            throws Exception {
        setupCookie();
        setupHttpSession();
        when(request.isRequestedSessionIdValid()).thenReturn(true);
        filter.doFilter(request, response, filterChain);
        verifyNoInteractions(serializer);
        verify(filterChain).doFilter(request, response);
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
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void notExistingSession_trackedCookiePresent_sessionInitialized()
            throws Exception {
        setupCookie();
        MockHttpSession httpSession = new MockHttpSession();
        when(request.getSession(false)).thenReturn(null, httpSession);
        when(request.getSession(true)).thenReturn(httpSession);
        when(request.isRequestedSessionIdValid()).thenReturn(false);
        when(request.getSession(true)).thenAnswer(i -> {
            sessionListener.sessionCreated(new HttpSessionEvent(httpSession));
            return httpSession;
        });
        filter.doFilter(request, response, filterChain);

        assertThat(httpSession.getAttribute(CurrentKey.COOKIE_NAME))
                .isEqualTo(cookie.getValue());
        verify(filterChain).doFilter(request, response);
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
        verify(filterChain).doFilter(request, response);
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
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void notExistingSession_trackedCookiePresent_concurrentRequest_sessionInitialized()
            throws Exception {
        // Slow down deserialization to simulate concurrent requests
        doAnswer(i -> {
            Thread.sleep(1000);
            return null;
        }).when(serializer).deserialize(any(), any());

        var execution1 = createTestRequest(cookie);
        var execution2 = createTestRequest(cookie);

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> execution1.execute(filter)),
                CompletableFuture.runAsync(() -> execution2.execute(filter)))
                .join();

        TestRequest sessionCreationExecution;
        TestRequest waitingExecution;
        if (execution1.session().get() != null) {
            sessionCreationExecution = execution1;
            waitingExecution = execution2;
        } else {
            sessionCreationExecution = execution2;
            waitingExecution = execution1;
        }

        verify(sessionCreationExecution.chain).doFilter(
                sessionCreationExecution.request(),
                sessionCreationExecution.response());
        verify(waitingExecution.response).sendRedirect(anyString(), eq(307));
        verify(waitingExecution.chain, never()).doFilter(
                waitingExecution.request(), waitingExecution.response());

        // Simulate the temporary redirect
        var redirect = createTestRequest(cookie);
        redirect.session().set(sessionCreationExecution.session().get());
        redirect.execute(filter);

        verify(redirect.chain).doFilter(redirect.request(),
                redirect.response());

    }

    @Test
    void filterDestroyed_destroyCallback_run() {
        filter.destroy();
        verify(sessionListener).stop();
    }

    private MockHttpSession setupHttpSession() {
        MockHttpSession httpSession = new MockHttpSession();
        when(request.getSession(false)).thenReturn(httpSession);
        return httpSession;
    }

    private void setupCookie() {
        when(request.getCookies()).thenReturn(new Cookie[] { cookie });
    }

    record TestRequest(HttpServletRequest request, HttpServletResponse response,
            AtomicReference<HttpSession> session, FilterChain chain) {
        void execute(Filter filter) {
            try {
                filter.doFilter(request, response, chain);
            } catch (ServletException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private TestRequest createTestRequest(Cookie cookie) {
        // lenient mock because stub calls might be hit or not
        HttpServletRequest request = mock(HttpServletRequest.class,
                Mockito.withSettings().strictness(Strictness.LENIENT));
        HttpServletResponse response = mock(HttpServletResponse.class);
        MockHttpSession httpSession = new MockHttpSession();
        AtomicReference<HttpSession> createdSession = new AtomicReference<>();
        when(request.getSession(true)).thenAnswer(i -> {
            createdSession.set(httpSession);
            sessionListener.sessionCreated(new HttpSessionEvent(httpSession));
            return httpSession;
        });
        when(request.getSession(false)).thenAnswer(i -> createdSession.get());
        when(request.getRequestedSessionId()).thenReturn("invalidSessionID");
        when(request.isRequestedSessionIdValid()).thenReturn(false);
        when(request.getCookies()).thenReturn(new Cookie[] { cookie });

        return new TestRequest(request, response, createdSession,
                mock(FilterChain.class));
    }
}
