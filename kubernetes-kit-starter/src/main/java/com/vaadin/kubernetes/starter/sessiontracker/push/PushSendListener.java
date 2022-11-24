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
 * Associates AtmosphereResource with a PushConnection identifier in order to be
 * able to reattach them later on.
 */
public interface PushSendListener {

    /**
     * Invoked whenever a UIDL message has been sent to the client.
     *
     * @param resource
     *            the {@link AtmosphereResource} used to process the message
     */
    void onMessageSent(AtmosphereResource resource);

}
