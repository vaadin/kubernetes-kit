package com.vaadin.kubernetes.starter.sessiontracker.backend;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.types.Expiration;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RedisConnectorTest {
    String clusterKey;
    RedisConnectionFactory factory;
    RedisConnection connection;
    RedisConnector connector;

    @BeforeEach
    void setUp() {
        clusterKey = UUID.randomUUID().toString();

        factory = mock(RedisConnectionFactory.class);
        connection = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(connection);

        connector = new RedisConnector(factory);
    }

    @Test
    void sendSession_sessionIsAdded() {
        SessionInfo sessionInfo = new SessionInfo(clusterKey,
                new byte[] { 'f', 'o', 'o' });

        connector.sendSession(sessionInfo);

        verify(connection).set(aryEq(RedisConnector.getKey(clusterKey)),
                aryEq(sessionInfo.getData()));
    }

    @Test
    void sendSession_expiration_sessionIsAddedWithTimeToLive() {
        SessionInfo sessionInfo = new SessionInfo(clusterKey,
                Duration.ofMinutes(30), new byte[] { 'f', 'o', 'o' });

        connector.sendSession(sessionInfo);

        verify(connection).set(aryEq(RedisConnector.getKey(clusterKey)),
                aryEq(sessionInfo.getData()),
                eq(Expiration.from(sessionInfo.getTimeToLive())),
                eq(RedisStringCommands.SetOption.UPSERT));
    }

    @Test
    void getSession_sessionIsRetrieved() {
        when(connection.exists(any(byte[].class))).thenReturn(false);

        connector.getSession(clusterKey);

        verify(connection).get(aryEq(RedisConnector.getKey(clusterKey)));
    }

    @Test
    void getSession_serializationInProgress_waitsForSerialization() {
        when(connection.exists(any(byte[].class))).thenReturn(true)
                .thenReturn(false);

        connector.getSession(clusterKey);

        verify(connection, times(2)).exists(any(byte[].class));
    }

    @Test
    void markSerializationStarted_sessionLocked() {
        connector.markSerializationStarted(clusterKey);

        verify(connection).set(aryEq(RedisConnector.getPendingKey(clusterKey)),
                any());
    }

    @Test
    void markSerializationComplete_sessionNotLocked() {
        connector.markSerializationComplete(clusterKey);

        verify(connection).del(aryEq(RedisConnector.getPendingKey(clusterKey)));
    }

    @Test
    void deleteSession_sessionNotLocked_sessionIsDeleted() {
        when(connection.exists(any(byte[].class))).thenReturn(false);

        connector.deleteSession(clusterKey);

        verify(connection).del(aryEq(RedisConnector.getKey(clusterKey)));
        verify(connection).del(aryEq(RedisConnector.getPendingKey(clusterKey)));
    }

    @Test
    void deleteSession_sessionLocked_waitsForSessionLock() {
        when(connection.exists(any(byte[].class))).thenReturn(true)
                .thenReturn(false);

        connector.deleteSession(clusterKey);

        verify(connection, times(2)).exists(any(byte[].class));
        verify(connection).del(aryEq(RedisConnector.getKey(clusterKey)));
        verify(connection).del(aryEq(RedisConnector.getPendingKey(clusterKey)));
    }

}
