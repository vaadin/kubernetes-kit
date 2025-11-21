package com.vaadin.kubernetes.starter.sessiontracker.backend;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.spi.properties.ClusterProperty;
import com.hazelcast.spi.properties.HazelcastProperty;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HazelcastConnectorTest {
    String clusterKey;
    IMap<String, byte[]> sessionMap;
    HazelcastInstance hazelcastInstance;
    HazelcastConnector connector;

    @BeforeEach
    void setUp() {
        clusterKey = UUID.randomUUID().toString();

        sessionMap = mock(IMap.class);

        // disable shutdown hook to prevent logs from being flooded with warning messages
        Config hazelcastConfig = new Config();
        hazelcastConfig.getProperties().setProperty(ClusterProperty.SHUTDOWNHOOK_ENABLED.getName(), "false");

        hazelcastInstance = mock(HazelcastInstance.class);
        when(hazelcastInstance.getConfig()).thenReturn(hazelcastConfig);
        when(hazelcastInstance.<String, byte[]> getMap(anyString()))
                .thenReturn(sessionMap);

        connector = new HazelcastConnector(hazelcastInstance);
    }

    @Test
    void sendSession_sessionIsAdded() {
        SessionInfo sessionInfo = new SessionInfo(clusterKey,
                new byte[] { 'f', 'o', 'o' });

        connector.sendSession(sessionInfo);

        verify(sessionMap).put(eq(HazelcastConnector.getKey(clusterKey)),
                aryEq(sessionInfo.getData()));
    }

    @Test
    void sendSession_expiration_sessionIsAddedWithTimeToLive() {
        SessionInfo sessionInfo = new SessionInfo(clusterKey,
                Duration.ofMinutes(30), new byte[] { 'f', 'o', 'o' });

        connector.sendSession(sessionInfo);

        verify(sessionMap).put(eq(HazelcastConnector.getKey(clusterKey)),
                aryEq(sessionInfo.getData()), eq(30L * 60),
                eq(TimeUnit.SECONDS));
    }

    @Test
    void getSession_sessionIsRetrieved() {
        when(sessionMap.isLocked(any())).thenReturn(false);

        connector.getSession(clusterKey);

        try {
            verify(sessionMap, never()).tryLock(any(), anyLong(), any(),
                    anyLong(), any());
        } catch (Exception e) {
            fail();
        }

        verify(sessionMap).get(eq(HazelcastConnector.getKey(clusterKey)));
    }

    @Test
    void getSession_sessionLocked_waitsForSessionLock() {
        when(sessionMap.isLocked(any())).thenReturn(true);
        try {
            when(sessionMap.tryLock(any(), anyLong(), any(), anyLong(), any()))
                    .thenReturn(true);
        } catch (Exception e) {
            fail();
        }

        connector.getSession(clusterKey);

        try {
            verify(sessionMap).tryLock(any(), anyLong(), any(), anyLong(),
                    any());
        } catch (Exception e) {
            fail();
        }

        verify(sessionMap).get(eq(HazelcastConnector.getKey(clusterKey)));
    }

    @Test
    void markSerializationStarted_zeroExpiration_sessionLockedWithoutTimeToLive() {
        connector.markSerializationStarted(clusterKey, Duration.ofMinutes(0));

        verify(sessionMap)
                .lock(eq(HazelcastConnector.getPendingKey(clusterKey)));
    }

    @Test
    void markSerializationStarted_validExpiration_sessionLockedWithTimeToLive() {
        connector.markSerializationStarted(clusterKey, Duration.ofMinutes(30));

        verify(sessionMap).lock(
                eq(HazelcastConnector.getPendingKey(clusterKey)), eq(30L * 60),
                eq(TimeUnit.SECONDS));
    }

    @Test
    void markSerializationComplete_sessionNotLocked() {
        connector.markSerializationComplete(clusterKey);

        verify(sessionMap)
                .forceUnlock(eq(HazelcastConnector.getPendingKey(clusterKey)));
    }

    @Test
    void markSerializationFailed_sessionNotLocked() {
        Throwable error = new RuntimeException("error");
        connector.markSerializationFailed(clusterKey, error);

        verify(sessionMap)
                .forceUnlock(eq(HazelcastConnector.getPendingKey(clusterKey)));
    }

    @Test
    void deleteSession_sessionNotLocked_sessionIsDeleted()
            throws InterruptedException {
        when(sessionMap.tryLock(
                eq(HazelcastConnector.getPendingKey(clusterKey)), anyLong(),
                any(), anyLong(), any())).thenReturn(true);

        connector.deleteSession(clusterKey);

        verify(sessionMap).delete(HazelcastConnector.getKey(clusterKey));
        verify(sessionMap).delete(HazelcastConnector.getPendingKey(clusterKey));
        verify(sessionMap, never()).tryLock(
                eq(HazelcastConnector.getPendingKey(clusterKey)), anyLong(),
                any(), anyLong(), any());
    }

    @Test
    void deleteSession_sessionLocked_waitsForSessionLock()
            throws InterruptedException {
        when(sessionMap.isLocked(any())).thenReturn(true);
        when(sessionMap.tryLock(
                eq(HazelcastConnector.getPendingKey(clusterKey)), anyLong(),
                any(), anyLong(), any())).thenReturn(true);

        connector.deleteSession(clusterKey);

        verify(sessionMap).tryLock(
                eq(HazelcastConnector.getPendingKey(clusterKey)), anyLong(),
                any(), anyLong(), any());
        verify(sessionMap).delete(HazelcastConnector.getKey(clusterKey));
        verify(sessionMap).delete(HazelcastConnector.getPendingKey(clusterKey));
    }

    @Test
    void markDeserializationStarted_notPending_returnTrue() {
        String pendingKey = HazelcastConnector
                .getDeserializationPendingKey(clusterKey);
        when(sessionMap.isLocked(pendingKey)).thenReturn(false);

        Duration timeToLive = Duration.ofSeconds(60);
        Assert.assertTrue(
                connector.markDeserializationStarted(clusterKey, timeToLive));

        verify(sessionMap).lock(eq(pendingKey), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void markDeserializationStarted_zeroExpiration_deserializationLockedWithoutTimeToLive() {
        String pendingKey = HazelcastConnector
                .getDeserializationPendingKey(clusterKey);
        when(sessionMap.isLocked(pendingKey)).thenReturn(false);

        Duration timeToLive = Duration.ofMinutes(0);
        connector.markDeserializationStarted(clusterKey, timeToLive);

        verify(sessionMap).lock(eq(pendingKey));
    }

    @Test
    void markDeserializationStarted_pending_returnFalse() {
        String pendingKey = HazelcastConnector
                .getDeserializationPendingKey(clusterKey);
        when(sessionMap.isLocked(pendingKey)).thenReturn(true);

        Duration timeToLive = Duration.ofSeconds(60);
        Assert.assertFalse(
                connector.markDeserializationStarted(clusterKey, timeToLive));

        verify(sessionMap, never()).lock(eq(pendingKey), anyLong(), any());
    }

    @Test
    void markDeserializationComplete_removePendingKey() {
        connector.markDeserializationComplete(clusterKey);

        verify(sessionMap).forceUnlock(eq(
                HazelcastConnector.getDeserializationPendingKey(clusterKey)));
    }

    @Test
    void markDeserializationFailed_removePendingKey() {
        Throwable error = new RuntimeException("error");
        connector.markDeserializationFailed(clusterKey, error);

        verify(sessionMap).forceUnlock(eq(
                HazelcastConnector.getDeserializationPendingKey(clusterKey)));
    }

}
