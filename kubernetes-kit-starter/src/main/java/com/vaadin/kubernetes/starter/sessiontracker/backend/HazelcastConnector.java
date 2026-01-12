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

import com.hazelcast.client.impl.clientside.ClientDynamicClusterConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.spi.properties.ClusterProperty;
import com.hazelcast.spi.properties.HazelcastProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.kubernetes.starter.ProductUtils;

public class HazelcastConnector implements BackendConnector {

    static {
        ProductUtils.markAsUsed(HazelcastConnector.class.getSimpleName());
    }

    private final IMap<String, byte[]> sessions;

    public HazelcastConnector(HazelcastInstance hazelcastInstance) {
        shutdownHookWarning(hazelcastInstance);
        sessions = hazelcastInstance.getMap("vaadin:sessions");
    }

    private static void shutdownHookWarning(
            HazelcastInstance hazelcastInstance) {
        Config config = hazelcastInstance.getConfig();
        if (!(config instanceof ClientDynamicClusterConfig)) {
            HazelcastProperties properties = new HazelcastProperties(
                    config.getProperties());
            if (properties.getBoolean(ClusterProperty.SHUTDOWNHOOK_ENABLED)) {
                getLogger()
                        .warn("""
                                Hazelcast shutdown hook is enabled. This is not recommended for production use because Hazelcast instance \
                                might be stopped before KubernetesKit can propagate the latest session state to the cluster. \
                                Please disable the shutdown hook by setting the `hazelcast.shutdownhook.enabled` property to `false`.
                                """);
            }
        }
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
    public void markSerializationStarted(String clusterKey,
            Duration timeToLive) {
        getLogger().debug("Marking serialization started for {}", clusterKey);
        String pendingKey = getPendingKey(clusterKey);
        lockPendingKey(timeToLive, pendingKey);
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
    public boolean markDeserializationStarted(String clusterKey,
            Duration timeToLive) {
        String pendingKey = getDeserializationPendingKey(clusterKey);
        if (sessions.isLocked(pendingKey)) {
            return false;
        }
        lockPendingKey(timeToLive, pendingKey);
        return true;
    }

    @Override
    public void markDeserializationComplete(String clusterKey) {
        getLogger().debug("Marking deserialization complete for {}",
                clusterKey);
        sessions.forceUnlock(getDeserializationPendingKey(clusterKey));
    }

    @Override
    public void markDeserializationFailed(String clusterKey, Throwable error) {
        getLogger().debug("Marking deserialization failed for {}", clusterKey,
                error);
        sessions.forceUnlock(getDeserializationPendingKey(clusterKey));
    }

    @Override
    public void deleteSession(String clusterKey) {
        getLogger().debug("Deleting session {}", clusterKey);
        waitForSerializationCompletion(clusterKey, "deleting");
        sessions.delete(getKey(clusterKey));
        sessions.delete(getPendingKey(clusterKey));
        getLogger().debug("Session {} deleted", clusterKey);
    }

    private void lockPendingKey(Duration timeToLive, String pendingKey) {
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            sessions.lock(pendingKey);
        } else {
            sessions.lock(pendingKey, timeToLive.toSeconds(), TimeUnit.SECONDS);
        }
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

    static String getDeserializationPendingKey(String clusterKey) {
        return "pending-deserialization-" + clusterKey;
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(HazelcastConnector.class);
    }

}
