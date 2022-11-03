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
                            "Unable to deserialize session from backend", e);
                }
            }
        }
    }

    static Logger getLogger() {
        return LoggerFactory.getLogger(SessionListener.class);
    }

}
