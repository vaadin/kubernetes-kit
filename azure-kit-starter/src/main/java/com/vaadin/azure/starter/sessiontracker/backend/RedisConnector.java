package com.vaadin.azure.starter.sessiontracker.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

public class RedisConnector implements BackendConnector {
    private final RedisConnectionFactory redisConnectionFactory;

    public RedisConnector(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    public void sendSession(SessionInfo sessionInfo) {
        getLogger().info("Sending session {} to Redis", sessionInfo.getClusterKey());
        RedisConnection connection = redisConnectionFactory.getConnection();
        connection.set(getKey(sessionInfo.getClusterKey()),
                sessionInfo.getData());
        getLogger().info("Session {} sent to redis", sessionInfo.getClusterKey());
    }

    private byte[] getKey(String clusterKey) {
        return BackendUtil.b("session-" + clusterKey);
    }

    public SessionInfo getSession(String clusterKey) {
        getLogger().info("Requesting session for {}", clusterKey);
        RedisConnection connection = redisConnectionFactory.getConnection();
        if (connection.exists(getPendingKey(clusterKey))) {
            long timeout = System.currentTimeMillis() + 5000;
            getLogger().info("Waiting for session to be serialized before using {}", clusterKey);
            while (connection.exists(getPendingKey(clusterKey)) && System.currentTimeMillis() < timeout) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            }
            if (System.currentTimeMillis() > timeout) {
                getLogger().warn(
                        "Gave up waiting for the serialization result. The host probably crashed during serialization");
            }
        }

        byte[] data = connection.get(getKey(clusterKey));
        if (data == null) {
            return null;
        }
        SessionInfo sessionInfo = new SessionInfo(clusterKey, data);

        getLogger().info("Received {}", sessionInfo);
        return sessionInfo;
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(RedisConnector.class);
    }

    @Override
    public void markSerializationStarted(String clusterKey) {
        getLogger().info("Marking serialization started for {}", clusterKey);
        RedisConnection connection = redisConnectionFactory.getConnection();
        connection.set(getPendingKey(clusterKey), BackendUtil.b("" + System.currentTimeMillis()));
    }

    @Override
    public void markSerializationComplete(String clusterKey) {
        getLogger().info("Marking serialization complete for {}", clusterKey);
        RedisConnection connection = redisConnectionFactory.getConnection();
        connection.del(getPendingKey(clusterKey));
    }

    private byte[] getPendingKey(String clusterKey) {
        return BackendUtil.b("pending-" + clusterKey);
    }

}
