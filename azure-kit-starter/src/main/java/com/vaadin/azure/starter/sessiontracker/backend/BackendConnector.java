package com.vaadin.azure.starter.sessiontracker.backend;

public interface BackendConnector {
    void sendSession(SessionInfo sessionInfo);

    SessionInfo getSession(String clusterKey);

    void deleteSession(String clusterKey);

    void markSerializationStarted(String clusterKey);

    void markSerializationComplete(String clusterKey);
}
