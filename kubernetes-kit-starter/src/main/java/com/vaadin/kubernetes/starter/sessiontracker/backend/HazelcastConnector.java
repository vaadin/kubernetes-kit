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

import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HazelcastConnector implements BackendConnector {

    private final IMap<String, byte[]> sessions;

    public HazelcastConnector(HazelcastInstance hazelcastInstance) {
        sessions = hazelcastInstance.getMap("vaadin:sessions");
    }

    @Override
    public void sendSession(SessionInfo sessionInfo) {
        getLogger().debug("Sending session {} to Hazelcast",
                sessionInfo.getClusterKey());
        String mapKey = getKey(sessionInfo.getClusterKey());
        sessions.put(mapKey, sessionInfo.getData());
        getLogger().debug("Session {} sent to Hazelcast",
                sessionInfo.getClusterKey());
    }

    @Override
    public SessionInfo getSession(String clusterKey) {
        getLogger().debug("Requesting session for {}", clusterKey);

        waitForSerializationCompletion(clusterKey, "getting session");

        byte[] data = sessions.get(getKey(clusterKey));
        if (data == null) {
            getLogger().debug("Session not found {}", clusterKey);
            return null;
        }
        SessionInfo sessionInfo = new SessionInfo(clusterKey, data);

        getLogger().debug("Received {}", sessionInfo);
        return sessionInfo;
    }

    @Override
    public void markSerializationStarted(String clusterKey) {
        getLogger().debug("Marking serialization started for {}", clusterKey);
        sessions.lock(getPendingKey(clusterKey));
    }

    @Override
    public void markSerializationComplete(String clusterKey) {
        getLogger().debug("Marking serialization complete for {}", clusterKey);
        sessions.forceUnlock(getPendingKey(clusterKey));
    }

    @Override
    public void deleteSession(String clusterKey) {
        getLogger().debug("Deleting session {}", clusterKey);
        waitForSerializationCompletion(clusterKey, "deleting");
        sessions.delete(getKey(clusterKey));
        sessions.delete(getPendingKey(clusterKey));
        getLogger().debug("Session {} deleted", clusterKey);
    }

    private void waitForSerializationCompletion(String clusterKey,
            String action) {
        String pendingKey = getPendingKey(clusterKey);
        if (sessions.isLocked(pendingKey)) {
            getLogger().debug(
                    "Waiting for session to be serialized before {} {}", action,
                    clusterKey);
            try {
                // Wait for pending serialization operation to complete
                sessions.tryLock(pendingKey, 5, TimeUnit.SECONDS, 1,
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                getLogger().warn(
                        "Gave up waiting for the serialization result of {} before {}. The host probably crashed during serialization",
                        clusterKey, action);
            }
        }
    }

    static String getKey(String clusterKey) {
        return "session-" + clusterKey;

    }

    static String getPendingKey(String clusterKey) {
        return "pending-" + clusterKey;
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(HazelcastConnector.class);
    }

}
