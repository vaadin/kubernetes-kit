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
import java.util.concurrent.ConcurrentHashMap;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
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

        String invalidSessionId = UUID.randomUUID().toString();
        var execution1 = createTestRequest(cookie, invalidSessionId);
        var execution2 = createTestRequest(cookie, invalidSessionId);

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
        verify(sessionCreationExecution.request).getSession(true);
        assertThat(sessionCreationExecution.session()).doesNotHaveNullValue();
        verify(sessionCreationExecution.response, never())
                .sendRedirect(anyString(), anyInt());

        verify(waitingExecution.response).sendRedirect(anyString(), eq(307));
        verify(waitingExecution.request, never()).getSession(true);
        verify(waitingExecution.chain, never()).doFilter(
                waitingExecution.request(), waitingExecution.response());
        assertThat(waitingExecution.session()).hasNullValue();

        // Simulate the temporary redirect still have the old session cookie
        var redirect = createTestRequest(cookie, invalidSessionId);
        redirect.execute(filter);
        verify(redirect.request, never()).getSession(true);
        verify(redirect.response).sendRedirect(anyString(), eq(307));
        verify(redirect.chain, never()).doFilter(waitingExecution.request(),
                waitingExecution.response());
        assertThat(redirect.session()).hasNullValue();

        // Simulate the temporary redirect with the correct session cookie
        redirect = createTestRequest(cookie,
                sessionCreationExecution.session().get().getId());
        redirect.execute(filter);
        verify(redirect.request, never()).getSession(true);
        verify(redirect.chain).doFilter(redirect.request(),
                redirect.response());
        assertThat(redirect.session())
                .hasValue(sessionCreationExecution.session().get());

        // Simulate a request from a browser that has the same cluster key but a
        // different requested session id
        // Should never happen unless the session cookie is manually altered on
        // the browser
        // In this case we allow creation of a new session to prevent infinite
        // redirect loop
        var requestWithDifferentSessionRequestedId = createTestRequest(cookie,
                "aDifferentSessionId");
        requestWithDifferentSessionRequestedId.execute(filter);
        verify(requestWithDifferentSessionRequestedId.response, never())
                .sendRedirect(anyString(), anyInt());
        verify(requestWithDifferentSessionRequestedId.request).getSession(true);
        verify(requestWithDifferentSessionRequestedId.chain).doFilter(
                requestWithDifferentSessionRequestedId.request(),
                requestWithDifferentSessionRequestedId.response());
        assertThat(requestWithDifferentSessionRequestedId.session())
                .doesNotHaveNullValue()
                .isNotSameAs(sessionCreationExecution.session().get());

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
            AtomicReference<HttpSession> session, String requestedSessionId,
            FilterChain chain) {

        void execute(Filter filter) {
            try {
                filter.doFilter(request, response, chain);
            } catch (ServletException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        String currentSessionId() {
            if (session.get() == null) {
                return requestedSessionId;
            }
            return session.get().getId();
        }

    }

    private final ConcurrentHashMap<String, HttpSession> sessionStore = new ConcurrentHashMap<>();

    private TestRequest createTestRequest(Cookie cookie,
            String requestedSessionId) {
        // lenient mock because stub calls might be hit or not
        HttpServletRequest request = mock(HttpServletRequest.class,
                Mockito.withSettings().strictness(Strictness.LENIENT));
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<HttpSession> createdSession = new AtomicReference<>();
        TestRequest testRequest = new TestRequest(request, response,
                createdSession, requestedSessionId, mock(FilterChain.class));

        when(request.getSession(true)).thenAnswer(i -> {
            String sid = testRequest.requestedSessionId();
            var session = sessionStore.get(sid);
            if (session != null) {
                return session;
            }
            MockHttpSession httpSession = new MockHttpSession();
            sessionStore.put(httpSession.getId(), httpSession);
            sessionListener.sessionCreated(new HttpSessionEvent(httpSession));
            testRequest.session().set(httpSession);
            return httpSession;
        });
        when(request.getSession(false)).then(i -> {
            HttpSession currentSession = testRequest.session().get();
            if (currentSession != null) {
                return currentSession;
            }
            HttpSession httpSession = sessionStore
                    .get(testRequest.requestedSessionId());
            testRequest.session().set(httpSession);
            return httpSession;
        });
        when(request.getRequestedSessionId())
                .then(i -> testRequest.requestedSessionId());
        when(request.isRequestedSessionIdValid()).then(
                i -> sessionStore.containsKey(testRequest.requestedSessionId));
        when(request.getCookies()).thenReturn(new Cookie[] { cookie });

        return testRequest;
    }
}
