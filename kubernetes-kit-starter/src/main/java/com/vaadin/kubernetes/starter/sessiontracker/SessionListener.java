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

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.kubernetes.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionInfo;

/**
 * An {@link HttpSessionListener} implementation that handles population and
 * destruction of session data stored on a distributed storage.
 *
 * In presence of a tracking Cookie set by {@link SessionTrackerFilter}, on
 * session creation, the distributed storage is queried for persisted attributes
 * that are then used to populate the newly created {@link HttpSession}.
 *
 * Data is fetched in binary format from the distributed storage by a
 * {@link BackendConnector} and then deserialized and copied into HTTP session
 * by {@link SessionSerializer}.
 *
 * When HTTP session is destroyed, also relative data on distribute storage is
 * deleted by the backend connector.
 *
 * @see SessionTrackerFilter
 * @see BackendConnector
 * @see SessionSerializer
 */
public class SessionListener implements HttpSessionListener {

    private final BackendConnector sessionBackendConnector;
    private final SessionSerializer sessionSerializer;

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
        HttpSession session = se.getSession();
        getLogger().debug("Session with id {} created", session.getId());
        String clusterKey = CurrentKey.get();
        if (clusterKey != null) {
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
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        HttpSession session = se.getSession();
        String sessionId = session.getId();
        getLogger().debug("Session with id {} destroyed", sessionId);
        SessionTrackerCookie.getFromSession(session).ifPresent(clusterKey -> {
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

    static Logger getLogger() {
        return LoggerFactory.getLogger(SessionListener.class);
    }

}
