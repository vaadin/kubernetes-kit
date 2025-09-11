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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.server.HandlerHelper.RequestType;
import com.vaadin.flow.shared.ApplicationConstants;
import com.vaadin.kubernetes.starter.KubernetesKitProperties;

/**
 * An HTTP filter implementation that serializes and persists HTTP session on a
 * distributed storage at the end of the request.
 * <p>
 * This filter uses a {@link SessionSerializer} component to serialize and
 * persist the HTTP session. Furthermore, it should be used in combination with
 * {@link SessionListener}, the component responsible for retrieve the session
 * attributes from the distributed storage and populate the HTTP session.
 * <p>
 * A unique identifier is assigned to valid HTTP sessions and sent to the client
 * as a tracking HTTP Cookie. The same identifier is used to associate the
 * session within the distributed storage.
 * <p>
 * If the HTTP request has not a valid session but the tracking Cookie exists,
 * the filter forces the creation of a new HTTP session, that is then populated
 * with data from the distributed storage by {@link SessionListener}.
 * <p>
 * The filter acts only on requests handled by Vaadin Flow.
 */
public class SessionTrackerFilter extends HttpFilter {

    private final transient SessionSerializer sessionSerializer;
    private final transient SessionListener sessionListener;
    private final transient KubernetesKitProperties properties;

    public SessionTrackerFilter(SessionSerializer sessionSerializer,
            SessionListener sessionListener,
            KubernetesKitProperties properties) {
        this.sessionSerializer = sessionSerializer;
        this.sessionListener = sessionListener;
        this.properties = properties;
    }

    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String cookieName = properties.getClusterKeyCookieName();
        SessionTrackerCookie.getValue(request, cookieName).ifPresent(key -> {
            CurrentKey.set(key);
            if (request.getSession(false) == null) {
                // Cluster key set but no session, create one, so it can be
                // imported if needed
                getLogger().info("Creating session for cluster key {}", key);
                request.getSession(true);
            }
        });
        try {
            HttpSession session = request.getSession(false);

            SessionTrackerCookie.setIfNeeded(session, request, response,
                    cookieName, cookieConsumer(request));
            super.doFilter(request, response, chain);

            if (session != null && request.isRequestedSessionIdValid()
                    && RequestType.UIDL.getIdentifier()
                            .equals(request.getParameter(
                                    ApplicationConstants.REQUEST_TYPE_PARAMETER))) {
                sessionSerializer.serialize(session);
            }
        } finally {
            CurrentKey.clear();
        }
    }

    @Override
    public void destroy() {
        sessionListener.stop();
        super.destroy();
    }

    private Consumer<Cookie> cookieConsumer(HttpServletRequest request) {
        return (Cookie cookie) -> {
            cookie.setHttpOnly(true);

            String path = request.getContextPath().isEmpty() ? "/"
                    : request.getContextPath();
            cookie.setPath(path);

            SameSite sameSite = properties.getClusterKeyCookieSameSite();
            if (sameSite != null && !sameSite.attributeValue().isEmpty()) {
                cookie.setAttribute("SameSite", sameSite.attributeValue());
            }
        };
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(getClass());
    }
}
