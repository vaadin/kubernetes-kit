/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Utility class to handle the storage of the distributed storage session key.
 */
public final class SessionTrackerCookie {

    private SessionTrackerCookie() {
    }

    /**
     * Sets the distributed storage session key on the HTTP session.
     *
     * If the Cookie does not exist, a new key is generated and the Cookie is
     * created and added to the HTTP response.
     *
     * @param session
     *            the HTTP session.
     * @param request
     *            the HTTP request.
     * @param response
     *            the HTTP response.
     * @param cookieConsumer
     *            function to apply custom setting to the cluster key cookie.
     * @deprecated use
     *             {@link #setIfNeeded(HttpSession, HttpServletRequest, HttpServletResponse, String, Consumer)}
     *             providing the cluster cookie name instead.
     */
    @Deprecated(since = "2.4", forRemoval = true)
    public static void setIfNeeded(HttpSession session,
            HttpServletRequest request, HttpServletResponse response,
            Consumer<Cookie> cookieConsumer) {
        setIfNeeded(session, request, response, CurrentKey.COOKIE_NAME,
                cookieConsumer);
    }

    /**
     * Sets the distributed storage session key on the HTTP session.
     *
     * If the Cookie does not exist, a new key is generated and the Cookie is
     * created and added to the HTTP response.
     *
     * @param session
     *            the HTTP session.
     * @param request
     *            the HTTP request.
     * @param response
     *            the HTTP response.
     * @param cookieName
     *            the name for the cluster cookie.
     * @param cookieConsumer
     *            function to apply custom setting to the cluster key cookie.
     */
    public static void setIfNeeded(HttpSession session,
            HttpServletRequest request, HttpServletResponse response,
            String cookieName, Consumer<Cookie> cookieConsumer) {
        cookieName = Objects.requireNonNullElse(cookieName,
                CurrentKey.COOKIE_NAME);
        Optional<Cookie> clusterKeyCookie = getCookie(request, cookieName);
        if (clusterKeyCookie.isEmpty()) {
            String clusterKey = UUID.randomUUID().toString();
            if (session != null) {
                session.setAttribute(CurrentKey.COOKIE_NAME, clusterKey);
            }
            Cookie cookie = new Cookie(cookieName, clusterKey);
            cookieConsumer.accept(cookie);
            response.addCookie(cookie);
        } else if (session != null
                && session.getAttribute(CurrentKey.COOKIE_NAME) == null) {
            String clusterKey = clusterKeyCookie.get().getValue();
            session.setAttribute(CurrentKey.COOKIE_NAME, clusterKey);
        }
    }

    /**
     * Gets the current distributed storage session key from HTTP session.
     *
     * @param session
     *            the HTTP session.
     * @return the current distributed storage session key wrapped into an
     *         {@link Optional}, or an empty Optional if the key does not exist.
     */
    public static Optional<String> getFromSession(HttpSession session) {
        return Optional.ofNullable(
                (String) session.getAttribute(CurrentKey.COOKIE_NAME));
    }

    private static Optional<Cookie> getCookie(HttpServletRequest request,
            String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Stream.of(cookies).filter(c -> c.getName().equals(cookieName))
                .findFirst();
    }

    /**
     * Gets the value of the current distributed storage session key from the
     * Cookie.
     *
     * @param request
     *            the HTTP request.
     * @return the current distributed storage session key wrapped into an
     *         {@link Optional}, or an empty Optional if the Cookie does not
     *         exist.
     * @deprecated use {@link #getValue(HttpServletRequest, String)} providing
     *             the cluster cookie name instead.
     */
    @Deprecated(since = "2.4", forRemoval = true)
    public static Optional<String> getValue(HttpServletRequest request) {
        return getValue(request, CurrentKey.COOKIE_NAME);
    }

    /**
     * Gets the value of the current distributed storage session key from the
     * Cookie.
     *
     * @param request
     *            the HTTP request.
     * @param cookieName
     *            the name of the cluster key cookie.
     * @return the current distributed storage session key wrapped into an
     *         {@link Optional}, or an empty Optional if the Cookie does not
     *         exist.
     */
    public static Optional<String> getValue(HttpServletRequest request,
            String cookieName) {
        return getCookie(request, cookieName).map(Cookie::getValue);
    }

}
