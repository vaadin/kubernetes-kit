package com.vaadin.azure.starter.sessiontracker.backend;

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
        getLogger().info("Sending session {} to Hazelcast",
                sessionInfo.getClusterKey());
        String mapKey = getKey(sessionInfo.getClusterKey());
        sessions.put(mapKey, sessionInfo.getData());
        getLogger().info("Session {} sent to Hazelcast",
                sessionInfo.getClusterKey());
    }

    @Override
    public SessionInfo getSession(String clusterKey) {
        getLogger().info("Requesting session for {}", clusterKey);

        String pendingKey = getPendingKey(clusterKey);
        if (sessions.isLocked(pendingKey)) {
            getLogger().info(
                    "Waiting for session to be serialized before using {}",
                    clusterKey);
            try {
                // Wait for pending serialization operation to complete
                sessions.tryLock(pendingKey, 5, TimeUnit.SECONDS, 1,
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                getLogger().warn(
                        "Gave up waiting for the serialization result. The host probably crashed during serialization");
            }
        }

        byte[] data = sessions.get(getKey(clusterKey));
        if (data == null) {
            getLogger().info("Session not found {}", clusterKey);
            return null;
        }
        SessionInfo sessionInfo = new SessionInfo(clusterKey, data);

        getLogger().info("Received {}", sessionInfo);
        return sessionInfo;
    }

    @Override
    public void markSerializationStarted(String clusterKey) {
        getLogger().info("Marking serialization started for {}", clusterKey);
        sessions.lock(getPendingKey(clusterKey));
    }

    @Override
    public void markSerializationComplete(String clusterKey) {
        getLogger().info("Marking serialization complete for {}", clusterKey);
        sessions.forceUnlock(getPendingKey(clusterKey));
    }

    private String getKey(String clusterKey) {
        return "session-" + clusterKey;

    }

    private String getPendingKey(String clusterKey) {
        return "pending-" + clusterKey;
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(HazelcastConnector.class);
    }

}
