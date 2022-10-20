package com.vaadin.azure.starter.push;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceSessionFactory;

/**
 * Associates AtmosphereResource with a PushConnection identifier in order to be
 * able to reattach them later on.
 */
public class CloudPushConnectionListener {

    public static final String PUSH_CONNECTION_ID_ATTRIBUTE = "PushConnectionID";

    private static final AtomicReference<AtmosphereConfig> atmosphereConfig = new AtomicReference<>();

    public void onConnect(AtmosphereResource resource,
            CloudPushConnection connection) {
        atmosphereConfig.compareAndSet(null, resource.getAtmosphereConfig());
        resource.getAtmosphereConfig().sessionFactory()
                .getSession(resource, true)
                .setAttribute(PUSH_CONNECTION_ID_ATTRIBUTE, connection.uuid);
    }

    public void onDisconnect(AtmosphereResource resource) {
        Optional.ofNullable(resource)
                .map(r -> r.getAtmosphereConfig().sessionFactory().getSession(r,
                        false))
                .ifPresent(
                        s -> s.setAttribute(PUSH_CONNECTION_ID_ATTRIBUTE, ""));
    }

    public void onSend(AtmosphereResource resource, String message) {
        // NO-OP by default
    }

    static void tryAttachAtmosphereResource(CloudPushConnection connection) {
        AtmosphereConfig atmosphere = atmosphereConfig.get();
        if (atmosphere != null) {
            String uuid = connection.uuid;
            AtmosphereResourceSessionFactory sessionFactory = atmosphere
                    .sessionFactory();
            atmosphere.resourcesFactory().findAll().stream()
                    .filter(r -> Optional
                            .ofNullable(sessionFactory.getSession(r, false))
                            .filter(session -> uuid.equals(session.getAttribute(
                                    PUSH_CONNECTION_ID_ATTRIBUTE)))
                            .isPresent())
                    .findFirst().ifPresent(connection::attachResource);
        }
    }

}
