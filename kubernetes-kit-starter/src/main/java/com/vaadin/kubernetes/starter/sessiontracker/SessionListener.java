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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.kubernetes.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionInfo;

/**
 * An {@link HttpSessionListener} implementation that handles population and
 * destruction of session data stored on a distributed storage.
 * <p>
 * In presence of a tracking Cookie set by {@link SessionTrackerFilter}, on
 * session creation, the distributed storage is queried for persisted attributes
 * that are then used to populate the newly created {@link HttpSession}.
 * <p>
 * Data is fetched in binary format from the distributed storage by a
 * {@link BackendConnector} and then deserialized and copied into HTTP session
 * by {@link SessionSerializer}.
 * <p>
 * When HTTP session is destroyed, also relative data on distribute storage is
 * deleted by the backend connector.
 *
 * @see SessionTrackerFilter
 * @see BackendConnector
 * @see SessionSerializer
 */
public class SessionListener implements HttpSessionListener,
        HttpSessionIdListener, HttpSessionAttributeListener {

    private final BackendConnector sessionBackendConnector;
    private final SessionSerializer sessionSerializer;
    // sessionID to clusterKey mapping
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, SessionCreationRequest> sessionCreationRequestMap = new ConcurrentHashMap<>();
    private boolean stopped = false;

    /**
     * Creates a new {@link SessionListener} instance.
     *
     * @param sessionBackendConnector
     *            backend connector to fetch and delete session data from
     *            distributed storage.
     * @param sessionSerializer
     *            component to perform deserialization of data from distributed
     *            storage.
     */
    public SessionListener(BackendConnector sessionBackendConnector,
            SessionSerializer sessionSerializer) {
        this.sessionBackendConnector = sessionBackendConnector;
        this.sessionSerializer = sessionSerializer;
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        if (stopped) {
            return;
        }
        HttpSession session = se.getSession();
        getLogger().debug("Session with id {} created", session.getId());
        String clusterKey = CurrentKey.get();
        if (clusterKey != null) {
            activeSessions.put(session.getId(), clusterKey);
            SessionInfo sessionInfo = sessionBackendConnector
                    .getSession(clusterKey);
            if (sessionInfo != null) {
                getLogger().debug("Found session {} on distributed storage",
                        clusterKey);
                try {
                    sessionSerializer.deserialize(sessionInfo, session);
                    getLogger().debug(
                            "HTTP session {} populated with data from {} key of distributed storage",
                            session.getId(), clusterKey);
                } catch (Exception e) {
                    getLogger().error(
                            "Unable to deserialize data with key {} from distributed storage into session {}",
                            clusterKey, session.getId(), e);
                }
            }
            sessionCreationRequestMap.put(clusterKey,
                    new SessionCreationRequest(session.getId(),
                            new HashSet<>()));
        } else {
            activeSessions.put(session.getId(), "");
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        if (stopped) {
            return;
        }
        HttpSession session = se.getSession();
        String sessionId = session.getId();
        String associatedClusterKey = activeSessions.remove(sessionId);
        if (associatedClusterKey != null) {
            sessionCreationRequestMap.remove(associatedClusterKey);
        }
        getLogger().debug("Session with id {} destroyed", sessionId);
        SessionTrackerCookie.getFromSession(session).ifPresent(clusterKey -> {
            sessionCreationRequestMap.remove(clusterKey);
            getLogger().debug(
                    "Deleting data with key {} from distributed storage associated to session {}",
                    clusterKey, sessionId);
            try {
                sessionBackendConnector.deleteSession(clusterKey);
                getLogger().debug(
                        "Deleted data with key {} from distributed storage associated to session {}",
                        clusterKey, sessionId);
            } catch (Exception e) {
                getLogger().error(
                        "Unable to delete data with key {} from distributed storage associated to session {}",
                        clusterKey, sessionId, e);
            }
        });
    }

    @Override
    public void sessionIdChanged(HttpSessionEvent event, String oldSessionId) {
        String newSessionId = event.getSession().getId();
        sessionCreationRequestMap
                .replaceAll((clusterKey,
                        current) -> oldSessionId.equals(current.sessionId())
                                ? new SessionCreationRequest(newSessionId,
                                        current.requestedSessionId())
                                : current);
        String clusterKey = activeSessions.remove(oldSessionId);
        activeSessions.put(newSessionId, clusterKey);
    }

    @Override
    public void attributeAdded(HttpSessionBindingEvent event) {
        updateClusterKeyToSessionAssociation(event);
    }

    @Override
    public void attributeReplaced(HttpSessionBindingEvent event) {
        updateClusterKeyToSessionAssociation(event);
    }

    private void updateClusterKeyToSessionAssociation(
            HttpSessionBindingEvent event) {
        if (CurrentKey.COOKIE_NAME.equals(event.getName())) {
            String clusterKey = (String) event.getValue();
            String sessionId = event.getSession().getId();
            getLogger().debug("Associating cluster key {} to session {}",
                    clusterKey, sessionId);
            updateOrCreateSessionCreationRequest(clusterKey, sessionId);
        }
    }

    private SessionCreationRequest updateOrCreateSessionCreationRequest(
            String clusterKey, String sessionId) {
        return sessionCreationRequestMap.compute(clusterKey,
                (unused, current) -> {
                    if (current == null) {
                        return new SessionCreationRequest(sessionId,
                                new HashSet<>());
                    } else {
                        return new SessionCreationRequest(sessionId,
                                current.requestedSessionId());
                    }
                });
    }

    /**
     * Retrieves the session ID mapped to the given cluster key, if available.
     *
     * @param clusterKey
     *            the key used to identify a specific cluster in the session
     *            context
     * @return an {@code Optional} containing the session ID associated with the
     *         given cluster key, or an empty {@code Optional} if no mapping
     *         exists
     */
    public Optional<String> mapSession(String clusterKey,
            HttpServletRequest request) {
        String requestedSessionId = request.getRequestedSessionId();
        if (requestedSessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionCreationRequestMap.get(clusterKey))
                .filter(req -> req.requestedSessionId()
                        .contains(requestedSessionId))
                .map(SessionCreationRequest::sessionId);
    }

    public void sessionCreated(String clusterKey, HttpSession session,
            HttpServletRequest request) {
        Objects.requireNonNull(clusterKey, "clusterKey must not be null");
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(request, "HTTP request must not be null");
        String requestedSessionId = request.getRequestedSessionId();
        String sessionId = session.getId();
        if (!clusterKey.equals(activeSessions.get(sessionId))) {
            throw new IllegalStateException("Session " + sessionId
                    + " is not associated with cluster key " + clusterKey);
        }
        getLogger().debug(
                "Session {} created for cluster key {} with requested session id {}",
                sessionId, clusterKey, requestedSessionId);
        if (sessionId != null) {
            updateOrCreateSessionCreationRequest(clusterKey, sessionId)
                    .requestedSessionId().add(requestedSessionId);
        }
    }

    /**
     * Gets a predicate that tests if the given identifier matches an active
     * HTTP session.
     *
     * @return a predicate to check if an HTTP session is active or not.
     */
    public Predicate<String> activeSessionChecker() {
        return activeSessions::containsKey;
    }

    /**
     * Stops this session listener to skip any further {@code HttpSessionEvent}
     * handling.
     */
    public void stop() {
        stopped = true;
    }

    static Logger getLogger() {
        return LoggerFactory.getLogger(SessionListener.class);
    }

    private record SessionCreationRequest(String sessionId,
            Set<String> requestedSessionId) {
    }

    private record RequestedSession(String sessionId,
            Set<String> requestedSessionId) {

    }
}
