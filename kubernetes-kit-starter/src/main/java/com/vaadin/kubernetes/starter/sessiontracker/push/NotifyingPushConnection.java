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

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.atmosphere.cpr.AtmosphereResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.di.Lookup;
import com.vaadin.flow.server.communication.AtmospherePushConnection;
import com.vaadin.flow.server.communication.PushConnection;
import com.vaadin.flow.server.communication.PushConnectionFactory;

/**
 * AtmospherePushConnection that notifies listeners when a message has been
 * processed.
 */
public class NotifyingPushConnection extends AtmospherePushConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotifyingPushConnection.class);

    /**
     * Creates an instance connected to the given UI.
     *
     * @param ui
     *            the UI to which this connection belongs
     */
    public NotifyingPushConnection(UI ui) {
        super(ui);
    }

    @Override
    public void connect(AtmosphereResource resource) {
        super.connect(resource);
        notifyPushListeners(listener -> listener.onConnect(resource));
    }

    @Override
    public void push(boolean async) {
        AtomicBoolean canPush = new AtomicBoolean(true);
        notifyPushListeners(
                listener -> canPush.compareAndSet(true, listener.canPush()));
        if (canPush.get()) {
            super.push(async);
        } else {
            LOGGER.debug("Push operation postponed by a PushSendListener");
        }
    }

    @Override
    protected void sendMessage(String message) {
        super.sendMessage(message);
        AtmosphereResource resource = getResource();
        notifyPushListeners(listener -> listener.onMessageSent(resource));
    }

    private void notifyPushListeners(Consumer<PushSendListener> action) {
        Collection<PushSendListener> pushSendListeners = null;
        try {
            pushSendListeners = getUI().getSession().getService().getContext()
                    .getAttribute(Lookup.class)
                    .lookupAll(PushSendListener.class);
        } catch (IllegalStateException ex) {
            String message = "Cannot get PushSendListener instances. Most likely application server is shutting down and the error can be ignored.";
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(message, ex);
            } else {
                LOGGER.debug(message);
            }
        }
        if (pushSendListeners != null) {
            pushSendListeners.forEach(action);
        }
    }

    /**
     * Service loader implementation to provide {@link NotifyingPushConnection}s
     */
    public static class Factory implements PushConnectionFactory {

        @Override
        public PushConnection apply(UI ui) {
            return new NotifyingPushConnection(ui);
        }
    }

}
