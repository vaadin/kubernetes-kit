package com.vaadin.kubernetes.starter.sessiontracker.backend;

import java.time.Duration;
import java.util.UUID;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.types.Expiration;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RedisConnectorTest {
    String clusterKey;
    RedisConnectionFactory factory;
    RedisConnection connection;
    RedisStringCommands stringCommands;
    RedisKeyCommands keyCommands;
    RedisConnector connector;

    @BeforeEach
    void setUp() {
        clusterKey = UUID.randomUUID().toString();

        factory = mock(RedisConnectionFactory.class);
        connection = mock(RedisConnection.class);
        stringCommands = mock(RedisStringCommands.class);
        keyCommands = mock(RedisKeyCommands.class);
        when(connection.stringCommands()).thenReturn(stringCommands);
        when(connection.keyCommands()).thenReturn(keyCommands);
        when(factory.getConnection()).thenReturn(connection);

        connector = new RedisConnector(factory);
    }

    @Test
    void sendSession_sessionIsAdded() {
        SessionInfo sessionInfo = new SessionInfo(clusterKey,
                new byte[] { 'f', 'o', 'o' });

        connector.sendSession(sessionInfo);

        verify(connection.stringCommands()).set(
                aryEq(RedisConnector.getKey(clusterKey)),
                aryEq(sessionInfo.getData()));
    }

    @Test
    void sendSession_expiration_sessionIsAddedWithTimeToLive() {
        SessionInfo sessionInfo = new SessionInfo(clusterKey,
                Duration.ofMinutes(30), new byte[] { 'f', 'o', 'o' });

        connector.sendSession(sessionInfo);

        verify(connection.stringCommands()).set(
                aryEq(RedisConnector.getKey(clusterKey)),
                aryEq(sessionInfo.getData()),
                eq(Expiration.from(sessionInfo.getTimeToLive())),
                eq(RedisStringCommands.SetOption.UPSERT));
    }

    @Test
    void getSession_sessionIsRetrieved() {
        when(connection.keyCommands().exists(any(byte[].class)))
                .thenReturn(false);

        connector.getSession(clusterKey);

        verify(connection.stringCommands())
                .get(aryEq(RedisConnector.getKey(clusterKey)));
    }

    @Test
    void getSession_serializationInProgress_waitsForSerialization() {
        when(connection.keyCommands().exists(any(byte[].class)))
                .thenReturn(true).thenReturn(false);

        connector.getSession(clusterKey);

        verify(connection.keyCommands(), times(2)).exists(any(byte[].class));
    }

    @Test
    void markSerializationStarted_zeroExpiration_sessionLockedWithoutTimeToLive() {
        Duration timeToLive = Duration.ofMinutes(0);
        connector.markSerializationStarted(clusterKey, timeToLive);

        verify(connection.stringCommands())
                .set(aryEq(RedisConnector.getPendingKey(clusterKey)), any());
    }

    @Test
    void markSerializationStarted_validExpiration_sessionLockedWithTimeToLive() {
        Duration timeToLive = Duration.ofMinutes(30);
        connector.markSerializationStarted(clusterKey, timeToLive);

        verify(connection.stringCommands()).set(
                aryEq(RedisConnector.getPendingKey(clusterKey)), any(),
                eq(Expiration.from(timeToLive)),
                eq(RedisStringCommands.SetOption.UPSERT));
    }

    @Test
    void markSerializationComplete_sessionNotLocked() {
        connector.markSerializationComplete(clusterKey);

        verify(connection.keyCommands())
                .del(aryEq(RedisConnector.getPendingKey(clusterKey)));
    }

    @Test
    void markSerializationFailed_sessionNotLocked() {
        Throwable error = new RuntimeException("error");
        connector.markSerializationFailed(clusterKey, error);

        verify(connection.keyCommands())
                .del(aryEq(RedisConnector.getPendingKey(clusterKey)));
    }

    @Test
    void deleteSession_sessionNotLocked_sessionIsDeleted() {
        when(connection.keyCommands().exists(any(byte[].class)))
                .thenReturn(false);

        connector.deleteSession(clusterKey);

        verify(connection.keyCommands())
                .del(aryEq(RedisConnector.getKey(clusterKey)));
        verify(connection.keyCommands())
                .del(aryEq(RedisConnector.getPendingKey(clusterKey)));
    }

    @Test
    void deleteSession_sessionLocked_waitsForSessionLock() {
        when(connection.keyCommands().exists(any(byte[].class)))
                .thenReturn(true).thenReturn(false);

        connector.deleteSession(clusterKey);

        verify(connection.keyCommands(), times(2)).exists(any(byte[].class));
        verify(connection.keyCommands())
                .del(aryEq(RedisConnector.getKey(clusterKey)));
        verify(connection.keyCommands())
                .del(aryEq(RedisConnector.getPendingKey(clusterKey)));
    }

    @Test
    void markDeserializationStarted_notPending_returnTrue() {
        when(connection.keyCommands().exists(any(byte[].class)))
                .thenReturn(false);

        Duration timeToLive = Duration.ofSeconds(60);
        Assert.assertTrue(
                connector.markDeserializationStarted(clusterKey, timeToLive));

        verify(connection.stringCommands()).set(
                aryEq(RedisConnector.getDeserializationPendingKey(clusterKey)),
                any(), eq(Expiration.from(timeToLive)),
                eq(RedisStringCommands.SetOption.UPSERT));

    }

    @Test
    void markDeserializationStarted_zeroExpiration_deserializationLockedWithoutTimeToLive() {
        when(connection.keyCommands().exists(any(byte[].class)))
                .thenReturn(false);

        Duration timeToLive = Duration.ofMinutes(0);
        connector.markDeserializationStarted(clusterKey, timeToLive);

        verify(connection.stringCommands()).set(
                aryEq(RedisConnector.getDeserializationPendingKey(clusterKey)),
                any());
    }

    @Test
    void markDeserializationStarted_pending_returnFalse() {
        when(connection.keyCommands().exists(any(byte[].class)))
                .thenReturn(true);

        Duration timeToLive = Duration.ofSeconds(60);
        Assert.assertFalse(
                connector.markDeserializationStarted(clusterKey, timeToLive));

        verify(connection.stringCommands(), never()).set(
                aryEq(RedisConnector.getDeserializationPendingKey(clusterKey)),
                any(), eq(Expiration.from(timeToLive)),
                eq(RedisStringCommands.SetOption.UPSERT));
    }

    @Test
    void markDeserializationComplete_removePendingKey() {
        connector.markDeserializationComplete(clusterKey);

        verify(connection.keyCommands()).del(
                aryEq(RedisConnector.getDeserializationPendingKey(clusterKey)));
    }

    @Test
    void markDeserializationFailed_removePendingKey() {
        Throwable error = new RuntimeException("error");
        connector.markDeserializationFailed(clusterKey, error);

        verify(connection.keyCommands()).del(
                aryEq(RedisConnector.getDeserializationPendingKey(clusterKey)));
    }

}
