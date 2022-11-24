/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker.backend;

public interface BackendConnector {
    void sendSession(SessionInfo sessionInfo);

    SessionInfo getSession(String clusterKey);

    void deleteSession(String clusterKey);

    void markSerializationStarted(String clusterKey);

    void markSerializationComplete(String clusterKey);
}
