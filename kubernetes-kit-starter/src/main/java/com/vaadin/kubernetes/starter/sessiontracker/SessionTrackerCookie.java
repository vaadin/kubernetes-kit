package com.vaadin.kubernetes.starter.sessiontracker;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class SessionTrackerCookie {

    private SessionTrackerCookie() {
    }

    public static void setIfNeeded(HttpSession session,
                                   HttpServletRequest request, HttpServletResponse response) {
        Optional<Cookie> clusterKeyCookie = getCookie(request);
        if (!clusterKeyCookie.isPresent()) {
            String clusterKey = UUID.randomUUID().toString();
            session.setAttribute(CurrentKey.COOKIE_NAME, clusterKey);
            response.addCookie(new Cookie(CurrentKey.COOKIE_NAME, clusterKey));
        } else if (session.getAttribute(CurrentKey.COOKIE_NAME) == null) {
            String clusterKey = clusterKeyCookie.get().getValue();
            session.setAttribute(CurrentKey.COOKIE_NAME, clusterKey);
        }

    }

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

    public static Optional<String> getValue(HttpServletRequest request) {
        return getCookie(request).map(Cookie::getValue);
    }

}
