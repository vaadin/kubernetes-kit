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
import java.util.Optional;
import java.util.UUID;
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
     */
    public static void setIfNeeded(HttpSession session,
            HttpServletRequest request, HttpServletResponse response) {
        Optional<Cookie> clusterKeyCookie = getCookie(request);
        if (clusterKeyCookie.isEmpty()) {
            String clusterKey = UUID.randomUUID().toString();
            session.setAttribute(CurrentKey.COOKIE_NAME, clusterKey);
            Cookie cookie = new Cookie(CurrentKey.COOKIE_NAME, clusterKey);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setAttribute("SameSite", "Strict");
            response.addCookie(cookie);
        } else if (session.getAttribute(CurrentKey.COOKIE_NAME) == null) {
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

    private static Optional<Cookie> getCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Stream.of(cookies)
                .filter(c -> c.getName().equals(CurrentKey.COOKIE_NAME))
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
     */
    public static Optional<String> getValue(HttpServletRequest request) {
        return getCookie(request).map(Cookie::getValue);
    }

}
