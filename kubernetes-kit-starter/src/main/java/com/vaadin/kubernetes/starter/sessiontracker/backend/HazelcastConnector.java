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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.kubernetes.starter.ProductUtils;
import com.vaadin.kubernetes.starter.sessiontracker.SessionDeserializationPendingException;

public class HazelcastConnector implements BackendConnector {

    static {
        ProductUtils.markAsUsed(HazelcastConnector.class.getSimpleName());
    }

    private final IMap<String, byte[]> sessions;
    private Runnable shutdownCallback;

    public HazelcastConnector(HazelcastInstance hazelcastInstance) {
        sessions = hazelcastInstance.getMap("vaadin:sessions");
        hazelcastInstance.getLifecycleService().addLifecycleListener(event -> {
            if (shutdownCallback != null && event
                    .getState() == com.hazelcast.core.LifecycleEvent.LifecycleState.SHUTTING_DOWN) {
                try {
                    getLogger().debug(
                            "================================= Running shutdown callback for hazelcast....");
                    shutdownCallback.run();
                    getLogger().debug(
                            "================================= Running shutdown callback for hazelcast DONE");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    public void onShutdown(Runnable callback) {
        this.shutdownCallback = callback;
    }

    @Override
    public void sendSession(SessionInfo sessionInfo) {
        getLogger().debug("Sending session {} to Hazelcast",
                sessionInfo.getClusterKey());
        String mapKey = getKey(sessionInfo.getClusterKey());
        Duration timeToLive = sessionInfo.getTimeToLive();
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            sessions.put(mapKey, sessionInfo.getData());
        } else {
            sessions.put(mapKey, sessionInfo.getData(), timeToLive.toSeconds(),
                    TimeUnit.SECONDS);
        }

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
    public void markDeserializationStarted(String clusterKey) {
        String deserializationPendingKey = getPendingKey(clusterKey)
                + "-deserialization";
        getLogger().debug("Checking deserialization lock {} for {}",
                deserializationPendingKey, clusterKey);
        if (sessions.isLocked(deserializationPendingKey)) {
            getLogger().debug("Someone is already deserializing {}",
                    clusterKey);
            throw new SessionDeserializationPendingException(
                    "Someone is already deserializing " + clusterKey);
        }
        sessions.lock(deserializationPendingKey);
        getLogger().debug("Deserialization lock {} acquired for {}",
                deserializationPendingKey, clusterKey);
    }

    @Override
    public void markDeserializationComplete(String clusterKey) {
        String deserializationPendingKey = getPendingKey(clusterKey)
                + "-deserialization";
        getLogger().debug(
                "Releasing deserialization lock {} for {} after completion",
                deserializationPendingKey, clusterKey);
        sessions.forceUnlock(deserializationPendingKey);
    }

    @Override
    public void markDeserializationFailed(String clusterKey, Throwable error) {
        String deserializationPendingKey = getPendingKey(clusterKey)
                + "-deserialization";
        getLogger().debug(
                "Releasing deserialization lock {} for {} after failure",
                deserializationPendingKey, clusterKey, error);
        sessions.forceUnlock(deserializationPendingKey);
    }

    @Override
    public void markSerializationStarted(String clusterKey,
            Duration timeToLive) {
        getLogger().debug("Marking serialization started for {}", clusterKey);
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            sessions.lock(getPendingKey(clusterKey));
        } else {
            sessions.lock(getPendingKey(clusterKey), timeToLive.toSeconds(),
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public void markSerializationComplete(String clusterKey) {
        getLogger().debug("Marking serialization complete for {}", clusterKey);
        sessions.forceUnlock(getPendingKey(clusterKey));
    }

    @Override
    public void markSerializationFailed(String clusterKey, Throwable error) {
        getLogger().debug("Marking serialization failed for {}", clusterKey,
                error);
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
