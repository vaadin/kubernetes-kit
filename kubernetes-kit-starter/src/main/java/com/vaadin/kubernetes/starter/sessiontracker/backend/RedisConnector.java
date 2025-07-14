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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.types.Expiration;

import com.vaadin.kubernetes.starter.ProductUtils;

public class RedisConnector implements BackendConnector {

    static {
        ProductUtils.markAsUsed(RedisConnector.class.getSimpleName());
    }

    private final RedisConnectionFactory redisConnectionFactory;

    public RedisConnector(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public void sendSession(SessionInfo sessionInfo) {
        getLogger().debug("Sending session {} to Redis",
                sessionInfo.getClusterKey());
        try (RedisConnection connection = redisConnectionFactory
                .getConnection()) {
            byte[] key = getKey(sessionInfo.getClusterKey());
            Duration timeToLive = sessionInfo.getTimeToLive();
            if (timeToLive.isZero() || timeToLive.isNegative()) {
                connection.stringCommands().set(key, sessionInfo.getData());
            } else {
                connection.stringCommands().set(key, sessionInfo.getData(),
                        Expiration.from(timeToLive),
                        RedisStringCommands.SetOption.UPSERT);
            }
            getLogger().debug("Session {} sent to Redis",
                    sessionInfo.getClusterKey());
        }
    }

    static byte[] getKey(String clusterKey) {
        return BackendUtil.b("session-" + clusterKey);
    }

    @Override
    public SessionInfo getSession(String clusterKey) {
        getLogger().debug("Requesting session for {}", clusterKey);
        try (RedisConnection connection = redisConnectionFactory
                .getConnection()) {
            waitForSerializationCompletion(clusterKey, "getting session",
                    connection);

            byte[] data = connection.stringCommands().get(getKey(clusterKey));
            if (data == null) {
                return null;
            }
            SessionInfo sessionInfo = new SessionInfo(clusterKey, data);

            getLogger().debug("Received {}", sessionInfo);
            return sessionInfo;
        }
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(RedisConnector.class);
    }

    @Override
    public void markSerializationStarted(String clusterKey,
            Duration timeToLive) {
        getLogger().debug("Marking serialization started for {}", clusterKey);
        try (RedisConnection connection = redisConnectionFactory
                .getConnection()) {
            if (timeToLive.isZero() || timeToLive.isNegative()) {
                connection.stringCommands().set(getPendingKey(clusterKey),
                        BackendUtil.b("" + System.currentTimeMillis()));
            } else {
                connection.stringCommands().set(getPendingKey(clusterKey),
                        BackendUtil.b("" + System.currentTimeMillis()),
                        Expiration.from(timeToLive),
                        RedisStringCommands.SetOption.UPSERT);
            }
        }
    }

    @Override
    public void markSerializationComplete(String clusterKey) {
        getLogger().debug("Marking serialization complete for {}", clusterKey);
        try (RedisConnection connection = redisConnectionFactory
                .getConnection()) {
            connection.keyCommands().del(getPendingKey(clusterKey));
        }
    }

    @Override
    public void deleteSession(String clusterKey) {
        getLogger().debug("Deleting session for {}", clusterKey);
        try (RedisConnection connection = redisConnectionFactory
                .getConnection()) {
            waitForSerializationCompletion(clusterKey, "deleting session",
                    connection);
            connection.keyCommands().del(getKey(clusterKey));
            connection.keyCommands().del(getPendingKey(clusterKey));
        }
    }

    static byte[] getPendingKey(String clusterKey) {
        return BackendUtil.b("pending-" + clusterKey);
    }

    private void waitForSerializationCompletion(String clusterKey,
            String action, RedisConnection connection) {
        byte[] pendingKey = getPendingKey(clusterKey);
        if (Boolean.TRUE.equals(connection.keyCommands().exists(pendingKey))) {
            long timeout = System.currentTimeMillis() + 5000;
            getLogger().debug(
                    "Waiting for session to be serialized before {} {}", action,
                    clusterKey);
            while (Boolean.TRUE
                    .equals(connection.keyCommands().exists(pendingKey))
                    && System.currentTimeMillis() < timeout) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
            if (System.currentTimeMillis() > timeout) {
                getLogger().warn(
                        "Gave up waiting for the serialization result of {} before {}. The host probably crashed during serialization",
                        clusterKey, action);
            }
        }
    }
}
