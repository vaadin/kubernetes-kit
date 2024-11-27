package com.vaadin.kubernetes.starter.sessiontracker.backend;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SessionInfoTest {
    @Test
    void construct_attributesAreSet() {
        String clusterKey = UUID.randomUUID().toString();
        byte[] data = new byte[] { 'f', 'o', 'o' };
        int timeout = 10;

        SessionInfo sessionInfo = new SessionInfo(clusterKey,
                Duration.ofSeconds(10), data);

        assertEquals(clusterKey, sessionInfo.getClusterKey());
        assertEquals(data, sessionInfo.getData());
        assertEquals(Duration.ofSeconds(timeout), sessionInfo.getTimeToLive());
    }
}
