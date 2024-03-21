package com.vaadin.kubernetes.starter.sessiontracker;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SessionTrackerCookieTest {

    @Test
    void setIfNeeded_nullCookies_attributeIsSetAndCookieIsConfigured() {
        HttpSession session = mock(HttpSession.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        @SuppressWarnings("unchecked")
        Consumer<Cookie> cookieConsumer = (Consumer<Cookie>) mock(
                Consumer.class);

        SessionTrackerCookie.setIfNeeded(session, request, response,
                cookieConsumer);

        verify(session).setAttribute(eq(CurrentKey.COOKIE_NAME), anyString());
        verify(cookieConsumer).accept(any());
        verify(response).addCookie(any());
    }

    @Test
    void setIfNeeded_emptyCookies_attributeIsSetAndCookieIsConfigured() {
        HttpSession session = mock(HttpSession.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[0]);
        HttpServletResponse response = mock(HttpServletResponse.class);
        @SuppressWarnings("unchecked")
        Consumer<Cookie> cookieConsumer = (Consumer<Cookie>) mock(
                Consumer.class);

        SessionTrackerCookie.setIfNeeded(session, request, response,
                cookieConsumer);

        verify(session).setAttribute(eq(CurrentKey.COOKIE_NAME), anyString());
        verify(cookieConsumer).accept(any());
        verify(response).addCookie(any());
    }

    @Test
    void setIfNeeded_nullSessionAttribute_attributeIsSet() {
        String clusterKey = UUID.randomUUID().toString();

        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(anyString())).thenReturn(null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie(CurrentKey.COOKIE_NAME, clusterKey) });
        HttpServletResponse response = mock(HttpServletResponse.class);
        Consumer<Cookie> cookieConsumer = (Cookie cookie) -> {
        };

        SessionTrackerCookie.setIfNeeded(session, request, response,
                cookieConsumer);

        verify(session).setAttribute(eq(CurrentKey.COOKIE_NAME),
                eq(clusterKey));
        verify(response, never()).addCookie(any());
    }

    @Test
    void setIfNeeded_nonNullSessionAttribute_attributeIsNotSet() {
        String clusterKey = UUID.randomUUID().toString();

        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(anyString())).thenReturn("foo");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie(CurrentKey.COOKIE_NAME, clusterKey) });
        HttpServletResponse response = mock(HttpServletResponse.class);
        Consumer<Cookie> cookieConsumer = (Cookie cookie) -> {
        };

        SessionTrackerCookie.setIfNeeded(session, request, response,
                cookieConsumer);

        verify(session, never()).setAttribute(any(), any());
        verify(response, never()).addCookie(any());
    }

    @Test
    void getFromSession_sessionAttributeIsReturned() {
        HttpSession session = mock(HttpSession.class);

        SessionTrackerCookie.getFromSession(session);

        verify(session).getAttribute(eq(CurrentKey.COOKIE_NAME));
    }

    @Test
    void getValue_nullCookies_emptyIsReturned() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(null);

        Optional<String> value = SessionTrackerCookie.getValue(request);

        verify(request).getCookies();
        assertEquals(Optional.empty(), value);
    }

    @Test
    void getValue_emptyCookies_emptyIsReturned() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[0]);

        Optional<String> value = SessionTrackerCookie.getValue(request);

        verify(request).getCookies();
        assertTrue(value.isEmpty());
    }

    @Test
    void getValue_valueIsReturned() {
        String clusterKey = UUID.randomUUID().toString();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie(CurrentKey.COOKIE_NAME, clusterKey) });

        Optional<String> value = SessionTrackerCookie.getValue(request);

        verify(request).getCookies();
        assertTrue(value.isPresent());
        assertEquals(clusterKey, value.get());
    }
}
