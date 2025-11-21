package com.vaadin.kubernetes.starter.sessiontracker.push;

import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.Broadcaster;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.di.Lookup;
import com.vaadin.flow.server.communication.PushConnection;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
public class NotifyingPushConnectionTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private UI ui;

    PushSendListener setUpTestListener() {
        PushSendListener listener = mock(PushSendListener.class);
        Lookup lookup = Lookup.of(listener, PushSendListener.class);
        when(ui.getSession().getService().getContext()
                .getAttribute(Lookup.class)).thenReturn(lookup);
        return listener;
    }

    @Test
    void sendMessage_listenersAreNotified() {
        PushSendListener listener = setUpTestListener();
        AtmosphereResource resource = createAtmosphereResource();

        NotifyingPushConnection connection = new NotifyingPushConnection(ui);
        connection.connect(resource);
        connection.sendMessage("foo");

        verify(listener).onConnect(eq(resource));
        verify(listener).onMessageSent(eq(resource));
    }

    @NotNull
    private static AtmosphereResource createAtmosphereResource() {
        AtmosphereResource resource = mock(AtmosphereResource.class,
                withSettings().strictness(Strictness.LENIENT));
        Broadcaster broadcaster = mock(Broadcaster.class);
        when(resource.getBroadcaster()).thenReturn(broadcaster);
        return resource;
    }

    @Test
    void factory_apply_returnsNotifyingPushConnection() {
        NotifyingPushConnection.Factory factory = new NotifyingPushConnection.Factory();
        PushConnection connection = factory.apply(ui);

        assertTrue(connection instanceof NotifyingPushConnection);
    }

    @Test
    void push_listenerPreventsMessageSending_messageIsNotSent() {
        AtmosphereResource resource = createAtmosphereResource();
        PushSendListener listener = setUpTestListener();
        when(listener.canPush()).thenReturn(false);

        NotifyingPushConnection connection = new NotifyingPushConnection(ui);
        connection.connect(resource);
        connection.push();

        verify(listener, never()).onMessageSent(eq(resource));
    }
}
