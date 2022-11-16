package com.vaadin.kubernetes.starter.sessiontracker.backend;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SessionInfoTest {
    @Test
    void construct_attributesAreSet() {
        String clusterKey = UUID.randomUUID().toString();
        byte[] data = new byte[] {'f','o','o'};

        SessionInfo sessionInfo = new SessionInfo(clusterKey, data);

        assertEquals(clusterKey, sessionInfo.getClusterKey());
        assertEquals(data, sessionInfo.getData());
    }
}
