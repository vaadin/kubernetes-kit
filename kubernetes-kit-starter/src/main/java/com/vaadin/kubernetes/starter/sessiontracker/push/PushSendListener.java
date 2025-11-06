/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker.push;

import org.atmosphere.cpr.AtmosphereResource;

/**
 * Component notified when a UIDL message is sent to the client via PUSH
 * mechanism.
 * <p>
 * </p>
 * The component is also notified when the PUSH connection is established in
 * order to perform initialization tasks for the connected resource.
 * <p>
 * </p>
 * Implementation must be thread safe, since method invocation may originate in
 * different threads.
 */
public interface PushSendListener {

    /**
     * Invoked when a new PUSH connection is established.
     *
     * @param resource
     *            the {@link AtmosphereResource} behind the PUSH connection.
     */
    default void onConnect(AtmosphereResource resource) {
    }

    /**
     * Invoked whenever a UIDL message has been sent to the client.
     *
     * @param resource
     *            the {@link AtmosphereResource} used to process the message
     */
    void onMessageSent(AtmosphereResource resource);

    default boolean postponePush(AtmosphereResource resource) {
        return false;
    }
}
