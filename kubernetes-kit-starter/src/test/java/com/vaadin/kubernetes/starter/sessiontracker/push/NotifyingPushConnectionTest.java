package com.vaadin.kubernetes.starter.sessiontracker.push;

import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.Broadcaster;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.di.Lookup;
import com.vaadin.flow.server.communication.PushConnection;
import com.vaadin.kubernetes.starter.util.MockUI;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NotifyingPushConnectionTest {
    @Test
    void sendMessage_listenersAreNotified() {
        MockUI ui = new MockUI();
        PushSendListener listener = mock(PushSendListener.class);
        Lookup lookup = Lookup.of(listener, PushSendListener.class);
        ui.getSession().getService().getContext()
                .setAttribute(Lookup.class, lookup);

        AtmosphereResource resource = mock(AtmosphereResource.class);
        Broadcaster broadcaster = mock(Broadcaster.class);
        when(resource.getBroadcaster()).thenReturn(broadcaster);

        NotifyingPushConnection connection = new NotifyingPushConnection(ui);
        connection.connect(resource);
        connection.sendMessage("foo");

        verify(listener).onMessageSent(eq(resource));
    }

    @Test
    void factory_apply_returnsNotifyingPushConnection() {
        NotifyingPushConnection.Factory factory = new NotifyingPushConnection.Factory();
        PushConnection connection = factory.apply(new MockUI());

        assertTrue(connection instanceof NotifyingPushConnection);
    }
}
