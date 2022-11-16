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
