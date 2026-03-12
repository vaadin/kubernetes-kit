package com.vaadin.kubernetes.starter.ui;

import java.io.IOException;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.RequestHandler;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClusterSupportTest {

    private ClusterSupport clusterSupport;
    private MockedStatic<CurrentInstance> currentInstanceMockedStatic;
    private MockedStatic<VaadinRequest> vaadinRequestMockedStatic;
    private MockedStatic<VaadinResponse> vaadinResponseMockedStatic;

    @Mock
    private ServiceInitEvent serviceInitEvent;
    @Mock
    private VaadinSession vaadinSession;
    @Mock
    private VaadinRequest vaadinRequest;
    @Mock
    private VaadinResponse vaadinResponse;

    @Captor
    private ArgumentCaptor<RequestHandler> requestHandlerArgCaptor;
    @Captor
    private ArgumentCaptor<Command> commandArgCaptor;
    @Captor
    private ArgumentCaptor<ComponentEventListener<VersionNotifier.SwitchVersionEvent>> componentEventListenerArgCaptor;

    @BeforeEach
    void setUp() {
        clusterSupport = new ClusterSupport("1.0.0", "INGRESSCOOKIE",
                "X-AppUpdate");
        currentInstanceMockedStatic = mockStatic(CurrentInstance.class);
        vaadinRequestMockedStatic = mockStatic(VaadinRequest.class);
        vaadinResponseMockedStatic = mockStatic(VaadinResponse.class);
    }

    @AfterEach
    void tearDown() {
        currentInstanceMockedStatic.close();
        vaadinRequestMockedStatic.close();
        vaadinResponseMockedStatic.close();
    }

    @Test
    void serviceInit_requestHandlerIsAdded() {
        ServiceInitEvent serviceInitEvent = mock(ServiceInitEvent.class);

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent).addRequestHandler(any());
    }

    @Test
    void serviceInit_withNoAppVersion_requestHandlerIsNotAdded() {
        ClusterSupport noVersionSupport = new ClusterSupport(null,
                "INGRESSCOOKIE", "X-AppUpdate");
        ServiceInitEvent serviceInitEvent = mock(ServiceInitEvent.class);

        noVersionSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent, never()).addRequestHandler(any());
    }

    @Test
    void handleRequest_versionNotifierIsRemoved_ifAlreadyPresent_And_VersionHeaderIsNull()
            throws IOException {
        WrappedSession wrappedSession = mock(WrappedSession.class);
        UI ui = mock(UI.class);
        VersionNotifier versionNotifier = mock(VersionNotifier.class);

        when(vaadinRequest.getHeader(ClusterSupport.UPDATE_VERSION_HEADER))
                .thenReturn(null);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        when(vaadinSession.getUIs()).thenReturn(Collections.singletonList(ui));
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        when(ui.getChildren()).thenReturn(Stream.of(versionNotifier));

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgCaptor.capture());
        commandArgCaptor.getValue().execute();
        verify(ui).remove((Component) any());
    }

    @Test
    void handleRequest_versionNotifierIsRemoved_ifAlreadyPresent_And_VersionHeaderIsEmpty()
            throws IOException {
        WrappedSession wrappedSession = mock(WrappedSession.class);
        UI ui = mock(UI.class);
        VersionNotifier versionNotifier = mock(VersionNotifier.class);

        when(vaadinRequest.getHeader(ClusterSupport.UPDATE_VERSION_HEADER))
                .thenReturn("");
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        when(vaadinSession.getUIs()).thenReturn(Collections.singletonList(ui));
        when(ui.getChildren()).thenReturn(Stream.of(versionNotifier));

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgCaptor.capture());
        commandArgCaptor.getValue().execute();
        verify(ui).remove((Component) any());
    }

    @Test
    void handleRequest_versionNotifierIsRemoved_ifAlreadyPresent_And_VersionHeaderEqualsAppVersion()
            throws IOException {
        WrappedSession wrappedSession = mock(WrappedSession.class);
        UI ui = mock(UI.class);
        VersionNotifier versionNotifier = mock(VersionNotifier.class);

        when(vaadinRequest.getHeader(ClusterSupport.UPDATE_VERSION_HEADER))
                .thenReturn("1.0.0");
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        when(vaadinSession.getUIs()).thenReturn(Collections.singletonList(ui));
        when(ui.getChildren()).thenReturn(Stream.of(versionNotifier));

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgCaptor.capture());
        commandArgCaptor.getValue().execute();
        verify(ui).remove((Component) any());
    }

    @Test
    void handleRequest_versionNotifierIsNotRemoved_ifAlreadyPresent_And_VersionHeaderNotEqualsAppVersion()
            throws IOException {
        WrappedSession wrappedSession = mock(WrappedSession.class);
        UI ui = mock(UI.class);
        VersionNotifier versionNotifier = mock(VersionNotifier.class);

        when(vaadinRequest.getHeader(ClusterSupport.UPDATE_VERSION_HEADER))
                .thenReturn("2.0.0");
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        when(vaadinSession.getUIs()).thenReturn(Collections.singletonList(ui));
        when(ui.getChildren()).thenReturn(Stream.of(versionNotifier));

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgCaptor.capture());
        commandArgCaptor.getValue().execute();
        verify(ui, never()).remove((Component) any());
    }

    @Test
    void handleRequest_versionNotifierIsAdded_ifNotPresent_And_VersionHeaderEqualsAppVersion()
            throws IOException {
        WrappedSession wrappedSession = mock(WrappedSession.class);
        UI ui = mock(UI.class);

        when(vaadinRequest.getHeader(ClusterSupport.UPDATE_VERSION_HEADER))
                .thenReturn("2.0.0");
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        when(vaadinSession.getUIs()).thenReturn(Collections.singletonList(ui));
        when(ui.getChildren()).thenReturn(Stream.empty());

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgCaptor.capture());
        commandArgCaptor.getValue().execute();
        verify(ui).add((Component) any());
    }

    @Test
    void handleRequest_usesConfiguredHeaderName() throws IOException {
        String customHeader = "X-AppVersion";
        ClusterSupport customClusterSupport = new ClusterSupport("1.0.0",
                "INGRESSCOOKIE", customHeader);
        WrappedSession wrappedSession = mock(WrappedSession.class);
        UI ui = mock(UI.class);

        when(vaadinRequest.getHeader(customHeader)).thenReturn("2.0.0");
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        when(vaadinSession.getUIs()).thenReturn(Collections.singletonList(ui));
        when(ui.getChildren()).thenReturn(Stream.empty());

        customClusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgCaptor.capture());
        commandArgCaptor.getValue().execute();
        verify(ui).add((Component) any());
    }

    @Test
    void onComponentEvent_usesConfiguredCookieName() throws IOException {
        String customCookieName = "my-gateway-cookie";
        ClusterSupport customClusterSupport = new ClusterSupport("1.0.0",
                customCookieName, "X-AppUpdate");
        WrappedSession wrappedSession = mock(WrappedSession.class);
        UI ui = mock(UI.class);
        VersionNotifier.SwitchVersionEvent switchVersionEvent = mock(
                VersionNotifier.SwitchVersionEvent.class);
        SwitchVersionListener switchVersionListener = mock(
                SwitchVersionListener.class);
        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor
                .forClass(Cookie.class);

        when(vaadinRequest.getHeader(ClusterSupport.UPDATE_VERSION_HEADER))
                .thenReturn("2.0.0");
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        vaadinResponseMockedStatic.when(VaadinResponse::getCurrent)
                .thenReturn(vaadinResponse);
        when(vaadinRequest.getWrappedSession()).thenReturn(wrappedSession);
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        when(vaadinSession.getUIs()).thenReturn(Collections.singletonList(ui));
        when(ui.getChildren()).thenReturn(Stream.empty());
        when(switchVersionListener.nodeSwitch(any(), any())).thenReturn(true);

        customClusterSupport.setSwitchVersionListener(switchVersionListener);
        customClusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        try (MockedConstruction<VersionNotifier> mockedVersionNotifier = mockConstruction(
                VersionNotifier.class)) {
            verify(vaadinSession).access(commandArgCaptor.capture());
            commandArgCaptor.getValue().execute();
            verify(mockedVersionNotifier.constructed().get(0))
                    .addSwitchVersionEventListener(
                            componentEventListenerArgCaptor.capture());
            componentEventListenerArgCaptor.getValue()
                    .onComponentEvent(switchVersionEvent);
        }
        verify(vaadinResponse).addCookie(cookieCaptor.capture());
        assertEquals(customCookieName, cookieCaptor.getValue().getName());
    }

    @ParameterizedTest(name = "{index} And_IfNodeSwitchIs_{0}_doAppCleanupIsCalled_{1}_times")
    @CsvSource({ "true, 1, 1, 1, 1", "false, 0, 0, 0, 0" })
    void onComponentEvent_removesStickyClusterCookieAndInvalidatesSession(
            boolean nodeSwitch, int doAppCleanupTimes, int addCookieTimes,
            int getWrappedSessionTimes, int invalidateTimes)
            throws IOException {
        WrappedSession wrappedSession = mock(WrappedSession.class);
        UI ui = mock(UI.class);
        VersionNotifier.SwitchVersionEvent switchVersionEvent = mock(
                VersionNotifier.SwitchVersionEvent.class);
        SwitchVersionListener switchVersionListener = mock(
                SwitchVersionListener.class);

        when(vaadinRequest.getHeader(ClusterSupport.UPDATE_VERSION_HEADER))
                .thenReturn("2.0.0");
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        vaadinResponseMockedStatic.when(VaadinResponse::getCurrent)
                .thenReturn(vaadinResponse);
        when(vaadinRequest.getWrappedSession()).thenReturn(wrappedSession);
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        when(vaadinSession.getUIs()).thenReturn(Collections.singletonList(ui));
        when(ui.getChildren()).thenReturn(Stream.empty());
        when(switchVersionListener.nodeSwitch(any(), any()))
                .thenReturn(nodeSwitch);

        clusterSupport.setSwitchVersionListener(switchVersionListener);
        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        try (MockedConstruction<VersionNotifier> mockedVersionNotifier = mockConstruction(
                VersionNotifier.class)) {
            verify(vaadinSession).access(commandArgCaptor.capture());
            commandArgCaptor.getValue().execute();
            verify(mockedVersionNotifier.constructed().get(0))
                    .addSwitchVersionEventListener(
                            componentEventListenerArgCaptor.capture());
            componentEventListenerArgCaptor.getValue()
                    .onComponentEvent(switchVersionEvent);
        }
        verify(switchVersionListener, times(doAppCleanupTimes)).doAppCleanup();
        verify(vaadinResponse, times(addCookieTimes)).addCookie(any());
        verify(vaadinRequest, times(getWrappedSessionTimes))
                .getWrappedSession();
        verify(vaadinRequest.getWrappedSession(), times(invalidateTimes))
                .invalidate();
    }
}
