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

/**
 * Holder for serialized session attributes.
 */
public class SessionInfo {
    private final String clusterKey;
    private final byte[] data;

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

}
