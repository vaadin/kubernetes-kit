package com.vaadin.azure.starter.sessiontracker;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.azure.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.azure.starter.sessiontracker.backend.SessionInfo;

public class SessionListener implements HttpSessionListener {

    private final BackendConnector sessionBackendConnector;
    private final SessionSerializer sessionSerializer;

    public SessionListener(BackendConnector sessionBackendConnector,
            SessionSerializer sessionSerializer) {
        this.sessionBackendConnector = sessionBackendConnector;
        this.sessionSerializer = sessionSerializer;
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        HttpSession session = se.getSession();
        getLogger().info("Session with id {} created", session.getId());
        String clusterKey = CurrentKey.get();
        if (clusterKey != null) {
            SessionInfo sessionInfo = sessionBackendConnector
                    .getSession(clusterKey);
            if (sessionInfo != null) {
                try {
                    sessionSerializer.deserialize(sessionInfo, session);
                } catch (Exception e) {
                    getLogger().error(
                            "Unable to deserialize session {} from backend",
                            session.getId(), e);
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
            getLogger().debug("Deleting session {} from backend", sessionId);
            try {
                sessionBackendConnector.deleteSession(clusterKey);
            } catch (Exception e) {
                getLogger().error("Unable to delete session {} from backend",
                        sessionId, e);
            }
        });
    }

    static Logger getLogger() {
        return LoggerFactory.getLogger(SessionListener.class);
    }

}
