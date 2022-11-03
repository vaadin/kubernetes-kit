package com.vaadin.azure.starter.sessiontracker.backend;

public class SessionInfo {
    private final String clusterKey;
    private final byte[] data;

    public SessionInfo(String clusterKey, byte[] data) {
        this.clusterKey = clusterKey;
        this.data = data;
    }

    public String getClusterKey() {
        return clusterKey;
    }

    public byte[] getData() {
        return data;
    }

}
