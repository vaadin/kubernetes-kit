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

/**
 * Holder for serialized session attributes.
 */
public class SessionInfo {
    private final String clusterKey;
    private final byte[] data;
    private final Duration timeToLive;

    /**
     * Creates a new {@link SessionInfo} for the given distributed storage key.
     *
     * @param clusterKey
     *            the distributed storage key.
     * @param data
     *            serialized session attributes in binary format.
     */
    public SessionInfo(String clusterKey, byte[] data) {
        this.clusterKey = clusterKey;
        this.data = data;
        this.timeToLive = Duration.ZERO;
    }

    /**
     * Creates a new {@link SessionInfo} for the given distributed storage key.
     *
     * @param clusterKey
     *            the distributed storage key.
     * @param timeToLive
     *            the maximum amount of time an inactive session should be
     *            preserved in the backed. A zero or negative value means the
     *            session should not be evicted.
     * @param data
     *            serialized session attributes in binary format.
     */
    public SessionInfo(String clusterKey, Duration timeToLive, byte[] data) {
        this.clusterKey = clusterKey;
        this.data = data;
        this.timeToLive = timeToLive;
    }

    /**
     * Gets the distributed storage key.
     *
     * @return the distributed storage key.
     */
    public String getClusterKey() {
        return clusterKey;
    }

    /**
     * Gets serialized session attributes in binary format.
     *
     * @return the serialized session attributes in binary format
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Gets the maximum amount of time an inactive session should be preserved
     * in the backed. A zero or negative value means the session should not be
     * evicted.
     *
     * @return the session time to live.
     */
    public Duration getTimeToLive() {
        return timeToLive;
    }
}
