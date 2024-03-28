package com.vaadin.kubernetes.starter.sessiontracker.push;

import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.Broadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.di.Lookup;
import com.vaadin.flow.server.communication.PushConnection;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NotifyingPushConnectionTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private UI ui;

    @Test
    void sendMessage_listenersAreNotified() {
        PushSendListener listener = mock(PushSendListener.class);
        Lookup lookup = Lookup.of(listener, PushSendListener.class);
        when(ui.getSession().getService().getContext()
                .getAttribute(Lookup.class)).thenReturn(lookup);

        AtmosphereResource resource = mock(AtmosphereResource.class);
        Broadcaster broadcaster = mock(Broadcaster.class);
        when(resource.getBroadcaster()).thenReturn(broadcaster);

        NotifyingPushConnection connection = new NotifyingPushConnection(ui);
        connection.connect(resource);
        connection.sendMessage("foo");

        verify(listener).onConnect(eq(resource));
        verify(listener).onMessageSent(eq(resource));
    }

    @Test
    void factory_apply_returnsNotifyingPushConnection() {
        NotifyingPushConnection.Factory factory = new NotifyingPushConnection.Factory();
        PushConnection connection = factory.apply(ui);

        assertTrue(connection instanceof NotifyingPushConnection);
    }
}
