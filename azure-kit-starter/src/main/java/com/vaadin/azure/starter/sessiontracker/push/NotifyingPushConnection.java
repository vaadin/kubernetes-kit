package com.vaadin.azure.starter.sessiontracker.push;

import org.atmosphere.cpr.AtmosphereResource;

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
    protected void sendMessage(String message) {
        super.sendMessage(message);
        AtmosphereResource resource = getResource();
        getUI().getSession().getService().getContext()
                .getAttribute(Lookup.class).lookupAll(PushSendListener.class)
                .forEach(listener -> listener.onMessageSent(resource));
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
