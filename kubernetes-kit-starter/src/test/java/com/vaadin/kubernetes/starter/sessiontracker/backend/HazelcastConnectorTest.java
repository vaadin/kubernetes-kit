package com.vaadin.kubernetes.starter.sessiontracker.backend;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
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

        hazelcastInstance = mock(HazelcastInstance.class);
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
    void markSerializationStarted_sessionLocked() {
        connector.markSerializationStarted(clusterKey, Duration.ofSeconds(5));

        verify(sessionMap).lock(
                eq(HazelcastConnector.getPendingKey(clusterKey)), eq(5L),
                eq(TimeUnit.SECONDS));
    }

    @Test
    void markSerializationComplete_sessionNotLocked() {
        connector.markSerializationComplete(clusterKey);

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
}
