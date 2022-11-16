package com.vaadin.kubernetes.starter.ui;

import javax.servlet.http.Cookie;
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

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
public class ClusterSupportTest {

    private ClusterSupport clusterSupport;
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
    private ArgumentCaptor<ComponentEventListener<VersionNotificator.SwitchVersionEvent>> componentEventListenerArgCaptor;

    @SystemStub
    private EnvironmentVariables environmentVariables;

    @BeforeEach
    void setUp() {
        clusterSupport = new ClusterSupport();
        vaadinRequestMockedStatic = mockStatic(VaadinRequest.class);
        vaadinResponseMockedStatic = mockStatic(VaadinResponse.class);
        environmentVariables.set(ClusterSupport.ENV_APP_VERSION, "1.0.0");
    }

    @AfterEach
    void tearDown() {
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
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgCaptor.capture());
        commandArgCaptor.getValue().execute();
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
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgCaptor.capture());
        commandArgCaptor.getValue().execute();
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
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgCaptor.capture());
        commandArgCaptor.getValue().execute();
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
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgCaptor.capture());
        commandArgCaptor.getValue().execute();
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

        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgCaptor.capture());
        commandArgCaptor.getValue().execute();
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
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        verify(vaadinSession).access(commandArgCaptor.capture());
        commandArgCaptor.getValue().execute();
        verify(ui, never()).add((Component) any());
    }

    @ParameterizedTest(name = "{index} And_IfNodeSwitchIs_{0}_doAppCleanupIsCalled_{1}_times")
    @CsvSource({ "true, 1, 4, 1", "false, 0, 1, 0" })
    void onComponentEvent_removesCookiesAndInvalidatesSession(boolean nodeSwitch,
            int doAppCleanupTimes, int addCookieTimes, int invalidateTimes) throws IOException {
        Cookie[] cookies = {
                new Cookie(ClusterSupport.UPDATE_VERSION_COOKIE, "2.0.0") };
        WrappedSession wrappedHttpSession = mock(WrappedSession.class);
        UI ui = mock(UI.class);
        VersionNotificator.SwitchVersionEvent switchVersionEvent = mock(
                VersionNotificator.SwitchVersionEvent.class);
        SwitchVersionListener switchVersionListener = mock(
                SwitchVersionListener.class);

        when(vaadinRequest.getCookies()).thenReturn(cookies);
        when(vaadinRequest.getWrappedSession()).thenReturn(wrappedHttpSession);
        vaadinRequestMockedStatic.when(VaadinRequest::getCurrent)
                .thenReturn(vaadinRequest);
        vaadinResponseMockedStatic.when(VaadinResponse::getCurrent)
                .thenReturn(vaadinResponse);
        when(vaadinSession.getUIs()).thenReturn(Collections.singletonList(ui));
        when(switchVersionListener.nodeSwitch(any(), any()))
                .thenReturn(nodeSwitch);

        clusterSupport.setSwitchVersionListener(switchVersionListener);
        clusterSupport.serviceInit(serviceInitEvent);

        verify(serviceInitEvent)
                .addRequestHandler(requestHandlerArgCaptor.capture());
        requestHandlerArgCaptor.getValue().handleRequest(vaadinSession,
                vaadinRequest, vaadinResponse);
        try (MockedConstruction<VersionNotificator> mockedVersionNotificator = mockConstruction(
                VersionNotificator.class)) {
            verify(vaadinSession).access(commandArgCaptor.capture());
            commandArgCaptor.getValue().execute();
            verify(mockedVersionNotificator.constructed().get(0))
                    .addSwitchVersionEventListener(
                            componentEventListenerArgCaptor.capture());
            componentEventListenerArgCaptor.getValue()
                    .onComponentEvent(switchVersionEvent);
        }
        verify(switchVersionListener, times(doAppCleanupTimes)).doAppCleanup();
        verify(vaadinResponse, times(addCookieTimes)).addCookie(any());
        verify(vaadinRequest, times(invalidateTimes)).getWrappedSession();
        verify(vaadinRequest.getWrappedSession(), times(invalidateTimes)).invalidate();
    }
}
