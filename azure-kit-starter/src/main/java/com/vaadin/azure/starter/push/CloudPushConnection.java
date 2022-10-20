package com.vaadin.azure.starter.push;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Consumer;

import org.atmosphere.cpr.AtmosphereResource;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.di.Lookup;
import com.vaadin.flow.internal.ReflectTools;
import com.vaadin.flow.server.communication.AtmospherePushConnection;
import com.vaadin.flow.server.communication.PushConnection;
import com.vaadin.flow.server.communication.PushConnectionFactory;

/**
 * AtmospherePushConnection that attempts to attach the AtmosphereResource after
 * being deserialized.
 *
 * Aim of this implementation is to verify if Vaadin PUSH works when
 * VaadinSession is deserialized at every request.
 */
public class CloudPushConnection extends AtmospherePushConnection {

    final String uuid;

    /**
     * Creates an instance connected to the given UI.
     *
     * @param ui
     *            the UI to which this connection belongs
     */
    public CloudPushConnection(UI ui) {
        super(ui);
        uuid = UUID.randomUUID().toString();
    }

    @Override
    public void connect(AtmosphereResource resource) {
        applyListener(l -> l.onConnect(resource, this));
        super.connect(resource);
    }

    @Override
    public void disconnect() {
        AtmosphereResource resource = getResource();
        applyListener(l -> l.onDisconnect(resource));
        super.disconnect();
    }

    @Override
    protected void sendMessage(String message) {
        AtmosphereResource resource = getResource();
        applyListener(l -> l.onSend(resource, message));
        super.sendMessage(message);
    }

    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        CloudPushConnectionListener.tryAttachAtmosphereResource(this);
    }

    private void applyListener(Consumer<CloudPushConnectionListener> action) {
        getUI().getSession().getService().getContext()
                .getAttribute(Lookup.class)
                .lookupAll(CloudPushConnectionListener.class).forEach(action);
    }

    /**
     * Hacky way to attach the give resource to a deserialized instance.
     * 
     * @param resource
     *            AtmosphereResource to attach to current instance
     */
    void attachResource(AtmosphereResource resource) {
        try {
            Field field = AtmospherePushConnection.class
                    .getDeclaredField("resource");
            ReflectTools.setJavaFieldValue(this, field, resource);

            if (resource.isSuspended()) {
                field = AtmospherePushConnection.class
                        .getDeclaredField("state");
                ReflectTools.setJavaFieldValue(this, field,
                        AtmospherePushConnection.State.CONNECTED);
            }
        } catch (NoSuchFieldException e) {
            // Should never happen
            throw new RuntimeException(e);
        }
    }

    /**
     * Service loader implementation to provide {@link CloudPushConnection}s
     */
    public static class Factory implements PushConnectionFactory {

        @Override
        public PushConnection apply(UI ui) {
            return new CloudPushConnection(ui);
        }
    }

}
