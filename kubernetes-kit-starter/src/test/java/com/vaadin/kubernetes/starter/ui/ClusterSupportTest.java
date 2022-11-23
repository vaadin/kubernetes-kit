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
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({ MockitoExtension.class, SystemStubsExtension.class })
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

    @SystemStub
    private EnvironmentVariables environmentVariables;

    @BeforeEach
    void setUp() {
        clusterSupport = new ClusterSupport();
        currentInstanceMockedStatic = mockStatic(CurrentInstance.class);
        vaadinRequestMockedStatic = mockStatic(VaadinRequest.class);
        vaadinResponseMockedStatic = mockStatic(VaadinResponse.class);
        environmentVariables.set(ClusterSupport.ENV_APP_VERSION, "1.0.0");
    }

    @AfterEach
    void tearDown() {
        currentInstanceMockedStatic.close();
        vaadinRequestMockedStatic.close();
        vaadinResponseMockedStatic.close();
    }

    @Test
    void serviceInit_clusterSupportIsSetInCurrentInstance() {
        ServiceInitEvent serviceInitEvent = mock(ServiceInitEvent.class);

        clusterSupport.serviceInit(serviceInitEvent);

        currentInstanceMockedStatic
                .verify(() -> CurrentInstance.set(any(), any()));
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
    void handleRequest_versionNotifierIsRemoved_ifAlreadyPresent_And_VersionHeaderIsNull()
            throws IOException {
        WrappedSession wrappedSession = mock(WrappedSession.class);
        UI ui = mock(UI.class);
        VersionNotifier versionNotifier = mock(VersionNotifier.class);

        when(vaadinRequest.getHeader(ClusterSupport.UPDATE_VERSION_HEADER))
                .thenReturn(null);
        when(vaadinRequest.getWrappedSession()).thenReturn(wrappedSession);
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
    void handleRequest_versionNotifierIsRemoved_ifAlreadyPresent_And_VersionHeaderIsEmpty()
            throws IOException {
        WrappedSession wrappedSession = mock(WrappedSession.class);
        UI ui = mock(UI.class);
        VersionNotifier versionNotifier = mock(VersionNotifier.class);

        when(vaadinRequest.getHeader(ClusterSupport.UPDATE_VERSION_HEADER))
                .thenReturn("");
        when(vaadinRequest.getWrappedSession()).thenReturn(wrappedSession);
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
        when(vaadinRequest.getWrappedSession()).thenReturn(wrappedSession);
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
        when(vaadinRequest.getWrappedSession()).thenReturn(wrappedSession);
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
        when(vaadinRequest.getWrappedSession()).thenReturn(wrappedSession);
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

    @ParameterizedTest(name = "{index} And_IfNodeSwitchIs_{0}_doAppCleanupIsCalled_{1}_times")
    @CsvSource({ "true, 1, 1, 2, 1", "false, 0, 0, 1, 0" })
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
        when(vaadinRequest.getWrappedSession()).thenReturn(wrappedSession);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        vaadinResponseMockedStatic.when(VaadinResponse::getCurrent)
                .thenReturn(vaadinResponse);
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
