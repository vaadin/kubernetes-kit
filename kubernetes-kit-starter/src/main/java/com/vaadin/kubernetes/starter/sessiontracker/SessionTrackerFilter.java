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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

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
    private final transient KubernetesKitProperties properties;
    private final transient Runnable destroyCallback;
    private SessionListener sessionListener;

    // Track which clients are currently creating sessions
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingSessionCreation = new ConcurrentHashMap<>();

    // Timeout for waiting threads (in seconds)
    private static final long SESSION_CREATION_TIMEOUT_SECONDS = 30;

    // Delay for session cookie to propagate to the client (in milliseconds)
    private static final int SESSION_COOKIE_PROPAGATION_DELAY_MS = 500;

    /**
     * Creates a new {@code SessionTrackerFilter}.
     *
     * @param sessionSerializer
     *            the {@link SessionSerializer} used to serialize and
     *            deserialize session data
     * @param properties
     *            the {@link KubernetesKitProperties} providing configuration
     *            for the filter
     * @param sessionListener
     *            the {@link SessionListener} instance that tracks HTTP sessions
     *            lifecycle.
     */
    public SessionTrackerFilter(SessionSerializer sessionSerializer,
            KubernetesKitProperties properties,
            SessionListener sessionListener) {
        this.sessionSerializer = sessionSerializer;
        this.properties = properties;
        this.destroyCallback = sessionListener::stop;
        this.sessionListener = sessionListener;
    }

    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String cookieName = properties.getClusterKeyCookieName();

        AtomicReference<Boolean> replayRequestRequired = new AtomicReference<>(
                false);

        SessionTrackerCookie.getValue(request, cookieName).ifPresent(key -> {
            boolean sessionExists = request.getSession(false) != null;
            if (!sessionExists) {
                getLogger().debug(
                        "Request with cluster key {} with requested session id {}: {}",
                        key, request.getRequestedSessionId(),
                        request.getRequestURI());

                // Use AtomicReference to detect if we created the future
                AtomicReference<CompletableFuture<String>> sessionCreatingFutureHolder = new AtomicReference<>();

                // Atomically check if session creation is pending or initiate
                // it
                CompletableFuture<String> existingFuture = pendingSessionCreation
                        .computeIfAbsent(key, k -> {
                            getLogger().debug(
                                    "Initiating session creation for cluster key {}",
                                    key);
                            return sessionListener.mapSession(key, request)
                                    .map(CompletableFuture::completedFuture)
                                    .orElseGet(() -> {
                                        CompletableFuture<String> newFuture = new CompletableFuture<>();
                                        sessionCreatingFutureHolder
                                                .set(newFuture);
                                        return newFuture;
                                    });
                        });

                // Check if this thread created the future (first request) or
                // found an existing one (concurrent request)
                if (sessionCreatingFutureHolder.get() != null) {
                    // This thread created the future - it's the first request
                    createNewSession(request, key, existingFuture);
                    // Note: completion happens after filter chain and
                    // serialization
                } else {
                    // Concurrent request: wait for session creation to complete
                    replayRequestRequired.set(waitForSessionCreation(request,
                            key, existingFuture));
                }
            } else {
                getLogger().debug(
                        "Session already exists for cluster key {} on request {}",
                        key, request.getRequestURI());
                pendingSessionCreation.remove(key);
            }
        });

        // If this is a waiting request, redirect to ensure it uses the new
        // session ID
        // The waiting thread only proceeds here after the first request has
        // flushed its response
        if (Boolean.TRUE.equals(replayRequestRequired.get())) {
            String redirectUrl = buildRedirectUrl(request);
            getLogger().debug(
                    "Redirecting current request session ID {} to {} to use the new session ID",
                    request.getRequestedSessionId(), redirectUrl);
            response.sendRedirect(redirectUrl, 307);
            return;
        }

        // Process the request
        CompletableFuture<String> futureToComplete = null;
        String clusterKey = CurrentKey.get();
        if (clusterKey != null) {
            futureToComplete = pendingSessionCreation.get(clusterKey);
        }

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

            // Complete the future after successfully processing and serializing
            // the session.
            // Flush the response buffer first to ensure the Set-Cookie header
            // is sent to the browser
            // before waiting threads proceed with their redirect. This
            // eliminates the need for
            // arbitrary Thread.sleep() delays and provides deterministic
            // synchronization.
            if (futureToComplete != null && !futureToComplete.isDone()) {
                try {
                    response.flushBuffer();
                    getLogger().debug(
                            "Response flushed, completing session creation future for cluster key {}",
                            clusterKey);
                } catch (IOException e) {
                    getLogger().warn(
                            "Failed to flush response for cluster key {}, completing future anyway",
                            clusterKey, e);
                    // Continue to complete the future even if flush fails to
                    // avoid blocking waiting threads
                }
                // Delay the completion of the future to allow the browser to
                // receive the new session ID
                String newSessionId = session != null ? session.getId() : null;
                futureToComplete.completeAsync(() -> newSessionId,
                        CompletableFuture.delayedExecutor(
                                SESSION_COOKIE_PROPAGATION_DELAY_MS,
                                TimeUnit.MILLISECONDS));
                pendingSessionCreation.remove(clusterKey);
            }
        } catch (Exception e) {
            // If an error occurs, propagate it to waiting threads
            if (futureToComplete != null && !futureToComplete.isDone()) {
                getLogger().error(
                        "Error processing request for cluster key {}, notifying waiting threads",
                        clusterKey, e);
                futureToComplete.completeExceptionally(e);
                pendingSessionCreation.remove(clusterKey);
            }
            throw e;
        } finally {
            CurrentKey.clear();
        }
    }

    /**
     * Waits for the creation of a new HTTP session from a distributed
     * environment. This method blocks until the session is created or a timeout
     * occurs. It returns true if the current requested session is not the newly
     * created, indicating that a redirect is required to replay the request
     * with the correct session cookie.
     *
     * @param request
     *            the HTTP request associated with the session creation
     * @param key
     *            the unique cluster key associated with the session
     * @param sessionCreationFuture
     *            a {@link CompletableFuture} representing the asynchronous
     *            operation for session creation
     * @return {@code true} if the session ID has changed and requires
     *         redirection, {@code false} otherwise
     * @throws RuntimeException
     *             if a timeout occurs, the thread is interrupted, or an error
     *             occurs during session creation
     */
    private boolean waitForSessionCreation(HttpServletRequest request,
            String key, CompletableFuture<String> sessionCreationFuture) {
        getLogger().debug(
                "Waiting for session creation for cluster key {} on request {} with requested session id {}",
                key, request.getRequestURI(), request.getRequestedSessionId());
        try {
            String newSessionId = sessionCreationFuture
                    .get(SESSION_CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            getLogger().debug(
                    "Session {} creation completed for cluster key {}, redirecting requested session {}",
                    newSessionId, key, request.getRequestedSessionId());
            return !newSessionId.equals(request.getRequestedSessionId());
        } catch (TimeoutException e) {
            getLogger().error(
                    "Timeout waiting for session creation for cluster key {}",
                    key);
            pendingSessionCreation.remove(key);
            throw new RuntimeException(
                    "Timeout waiting for session creation for cluster key: "
                            + key,
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLogger().error(
                    "Interrupted while waiting for session creation for cluster key {}",
                    key);
            throw new RuntimeException(
                    "Interrupted while waiting for session creation", e);
        } catch (ExecutionException e) {
            getLogger().error(
                    "Error during session creation for cluster key {}", key,
                    e.getCause());
            throw new RuntimeException("Error during session creation",
                    e.getCause());
        }
    }

    private void createNewSession(HttpServletRequest request, String key,
            CompletableFuture<String> existingFuture) {
        CurrentKey.set(key);
        getLogger().debug(
                "Creating session for cluster key {} on request {} for requested session id {}",
                key, request.getRequestURI(), request.getRequestedSessionId());
        try {
            HttpSession session = request.getSession(true);
            sessionListener.sessionCreated(key, session, request);
            getLogger().debug("Session created successfully for cluster key {}",
                    key);
        } catch (RuntimeException e) {
            getLogger().error("Failed to create session for cluster key {}",
                    key, e);
            pendingSessionCreation.remove(key);
            existingFuture.completeExceptionally(e);
            throw e;
        }
    }

    @Override
    public void destroy() {
        if (destroyCallback != null) {
            destroyCallback.run();
        }
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

    /**
     * Builds a redirect URL that respects reverse proxy headers.
     * <p>
     * This method manually checks X-Forwarded-* headers commonly used by
     * reverse proxies and load balancers to preserve the original request
     * information, then uses Spring's {@link UriComponentsBuilder} to construct
     * a well-formed URL.
     * <p>
     * Note: This implementation does not rely on
     * {@link org.springframework.web.filter.ForwardedHeaderFilter} being
     * configured, making it work in all deployment scenarios.
     *
     * @param request
     *            the HTTP request
     * @return the full redirect URL including scheme, host, port, path, and
     *         query string
     */
    private String buildRedirectUrl(HttpServletRequest request) {
        // Check for X-Forwarded-Proto header (common with reverse proxies)
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isEmpty()) {
            scheme = request.getScheme();
        }

        // Check for X-Forwarded-Host header
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isEmpty()) {
            host = request.getHeader("Host");
            if (host == null || host.isEmpty()) {
                host = request.getServerName();
            }
        }

        // Check for X-Forwarded-Port header
        int port = -1;
        String portHeader = request.getHeader("X-Forwarded-Port");
        if (portHeader != null && !portHeader.isEmpty()) {
            try {
                port = Integer.parseInt(portHeader);
            } catch (NumberFormatException e) {
                // Ignore invalid port
            }
        }
        if (port == -1) {
            port = request.getServerPort();
        }

        // Use Spring's UriComponentsBuilder for clean URL construction
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .scheme(scheme).host(host).port(port)
                .path(request.getRequestURI());

        // Add query string if present
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            builder.query(queryString);
        }

        return builder.build().toUriString();
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(getClass());
    }
}
