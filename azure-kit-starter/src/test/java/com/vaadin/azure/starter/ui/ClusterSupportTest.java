package com.vaadin.azure.starter.ui;

import javax.servlet.http.Cookie;
import java.io.IOException;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.RequestHandler;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SystemStubsExtension.class)
public class ClusterSupportTest {

    private ClusterSupport clusterSupport;
    private ServiceInitEvent serviceInitEvent;
    private VaadinSession vaadinSession;
    private VaadinRequest vaadinRequest;
    private VaadinResponse vaadinResponse;
    private MockedStatic<VaadinRequest> vaadinRequestMockedStatic;
    private ArgumentCaptor<RequestHandler> requestHandlerArgumentCaptor;
    private ArgumentCaptor<Command> commandArgumentCaptor;

    @SystemStub
    private EnvironmentVariables environmentVariables;

    @BeforeEach
    void setUp() {
        clusterSupport = new ClusterSupport();
        serviceInitEvent = mock(ServiceInitEvent.class);
        vaadinSession = mock(VaadinSession.class);
        vaadinRequest = mock(VaadinRequest.class);
        vaadinResponse = mock(VaadinResponse.class);
        vaadinRequestMockedStatic = mockStatic(VaadinRequest.class);
        environmentVariables.set(ClusterSupport.ENV_APP_VERSION, "1.0.0");
        requestHandlerArgumentCaptor = ArgumentCaptor
                .forClass(RequestHandler.class);
        commandArgumentCaptor = ArgumentCaptor.forClass(Command.class);
    }

    @AfterEach
    void tearDown() {
        vaadinRequestMockedStatic.close();
    }

    @Test
    void serviceInit_requestHandlerIsAdded() {
        ServiceInitEvent serviceInitEvent = mock(ServiceInitEvent.class);

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent).addRequestHandler(any());
    }

    @Test
    void serviceInit_withNoAppVersion_requestHandlerIsNotAdded() {
        environmentVariables.set(ClusterSupport.ENV_APP_VERSION, null);
        ServiceInitEvent serviceInitEvent = mock(ServiceInitEvent.class);

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent, never()).addRequestHandler(any());
    }

    @Test
    void handleRequest_addsCurrentVersionCookie_ifNotPresent()
            throws IOException {
        Cookie[] cookies = {};

        when(vaadinRequest.getCookies()).thenReturn(cookies);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgumentCaptor.capture());
        requestHandlerArgumentCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgumentCaptor.capture());
        commandArgumentCaptor.getValue().execute();
        verify(vaadinResponse).addCookie(any());
    }

    @Test
    void handleRequest_addsCurrentVersionCookie_ifAppVersionIsDifferent()
            throws IOException {
        Cookie[] cookies = {
                new Cookie(ClusterSupport.CURRENT_VERSION_COOKIE, "1.1.0") };

        when(vaadinRequest.getCookies()).thenReturn(cookies);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgumentCaptor.capture());
        requestHandlerArgumentCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgumentCaptor.capture());
        commandArgumentCaptor.getValue().execute();
        verify(vaadinResponse).addCookie(any());
    }

    @Test
    void handleRequest_currentVersionCookieNotAdded_ifAppVersionIsSame()
            throws IOException {
        Cookie[] cookies = {
                new Cookie(ClusterSupport.CURRENT_VERSION_COOKIE, "1.0.0") };

        when(vaadinRequest.getCookies()).thenReturn(cookies);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgumentCaptor.capture());
        requestHandlerArgumentCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgumentCaptor.capture());
        commandArgumentCaptor.getValue().execute();
        verify(vaadinResponse, never()).addCookie(any());
    }

    @Test
    void handleRequest_versionNotificatorAdded_ifUpdateVersionCookieIsPresent()
            throws IOException {
        Cookie[] cookies = {
                new Cookie(ClusterSupport.UPDATE_VERSION_COOKIE, "2.0.0") };

        when(vaadinRequest.getCookies()).thenReturn(cookies);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        UI ui = mock(UI.class);
        when(vaadinSession.getUIs()).thenReturn(Collections.singletonList(ui));

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgumentCaptor.capture());
        requestHandlerArgumentCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgumentCaptor.capture());
        commandArgumentCaptor.getValue().execute();
        verify(ui).add((Component) any());
    }

    @Test
    void handleRequest_versionNotificatorNotAdded_ifUpdateVersionCookieEqualsCurrentVersionCookie()
            throws IOException {
        Cookie[] cookies = {
                new Cookie(ClusterSupport.CURRENT_VERSION_COOKIE, "1.0.0"),
                new Cookie(ClusterSupport.UPDATE_VERSION_COOKIE, "1.0.0") };

        when(vaadinRequest.getCookies()).thenReturn(cookies);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        UI ui = mock(UI.class);
        when(vaadinSession.getUIs()).thenReturn(Collections.singletonList(ui));

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgumentCaptor.capture());
        requestHandlerArgumentCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgumentCaptor.capture());
        commandArgumentCaptor.getValue().execute();
        verify(ui, never()).add((Component) any());
    }

    @Test
    void handleRequest_versionNotificatorNotAdded_ifAlreadyPresent()
            throws IOException {
        Cookie[] cookies = {
                new Cookie(ClusterSupport.UPDATE_VERSION_COOKIE, "2.0.0") };

        when(vaadinRequest.getCookies()).thenReturn(cookies);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        UI ui = mock(UI.class);
        when(vaadinSession.getUIs()).thenReturn(Collections.singletonList(ui));
        when(ui.getChildren()).thenReturn(
                Stream.of(new VersionNotificator("1.0.0", "2.0.0")));

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgumentCaptor.capture());
        requestHandlerArgumentCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgumentCaptor.capture());
        commandArgumentCaptor.getValue().execute();
        verify(ui, never()).add((Component) any());
    }
}
