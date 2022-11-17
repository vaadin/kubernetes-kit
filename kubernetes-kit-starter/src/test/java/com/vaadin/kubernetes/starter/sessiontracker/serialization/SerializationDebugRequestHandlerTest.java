package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.core.net.server.ServerRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpSession;

import com.vaadin.flow.component.PushConfiguration;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.function.SerializableRunnable;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedHttpSession;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import com.vaadin.flow.shared.ApplicationConstants;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.kubernetes.starter.ui.SessionDebugNotificator;

import static com.vaadin.kubernetes.starter.sessiontracker.serialization.SerializationDebugRequestHandler.SERIALIZATION_TEST_REQUEST_ATTRIBUTE_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class SerializationDebugRequestHandlerTest {

    SerializationDebugRequestHandler handler;

    private ApplicationConfiguration appConfig;
    private VaadinSession vaadinSession;
    private VaadinRequest request;
    private VaadinResponse response;
    private AtomicReference<SerializationDebugRequestHandler.Result> resultHolder;
    private HttpSession httpSession;
    private HttpServletRequest httpRequest;

    private SerializationDebugRequestHandler.Runner toolRunner;

    @BeforeEach
    void setUp() {
        handler = new SerializationDebugRequestHandler();
        httpSession = new MockHttpSession();
        VaadinService vaadinService = mock(VaadinService.class);
        VaadinContext vaadinContext = mock(VaadinContext.class);
        when(vaadinService.getContext()).thenReturn(vaadinContext);

        PushConfiguration pushConfiguration = mock(PushConfiguration.class);
        when(pushConfiguration.getPushMode()).thenReturn(PushMode.AUTOMATIC);
        UI ui = spy(new UI());
        ui.add(new SessionDebugNotificator() {
            @Override
            public void publishResults(
                    SerializationDebugRequestHandler.Result result) {
                resultHolder.set(result);
            }
        });
        doAnswer(i -> {
            SerializableConsumer<SerializationDebugRequestHandler.Result> consumer = resultHolder::set;
            return consumer;
        }).when(ui).accessLater(any(SerializableConsumer.class),
                any(SerializableRunnable.class));
        when(ui.getPushConfiguration()).thenReturn(pushConfiguration);
        when(vaadinService.findUI(any())).thenReturn(ui);

        appConfig = mock(ApplicationConfiguration.class);
        when(vaadinContext.getAttribute(eq(ApplicationConfiguration.class),
                any())).thenReturn(appConfig);
        when(appConfig.isProductionMode()).thenReturn(false);
        when(appConfig.isDevModeSessionSerializationEnabled()).thenReturn(true);

        vaadinSession = mock(VaadinSession.class);
        when(vaadinSession.getService()).thenReturn(vaadinService);
        when(vaadinSession.getSession())
                .thenReturn(new WrappedHttpSession(httpSession));
        doAnswer(i -> {
            i.<Command> getArgument(0).execute();
            return null;
        }).when(vaadinSession).accessSynchronously(any());

        request = mock(VaadinRequest.class);
        when(request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER))
                .thenReturn(HandlerHelper.RequestType.UIDL.getIdentifier());
        doAnswer(i -> {
            toolRunner = i.getArgument(1);
            return null;
        }).when(request).setAttribute(
                eq(SERIALIZATION_TEST_REQUEST_ATTRIBUTE_KEY),
                any(SerializationDebugRequestHandler.Runner.class));
        httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute(SERIALIZATION_TEST_REQUEST_ATTRIBUTE_KEY))
                .thenReturn(toolRunner);
        when(httpRequest.getSession(false)).thenReturn(httpSession);
        when(httpRequest.isRequestedSessionIdValid()).thenReturn(true);
        resultHolder = new AtomicReference<>();
        /*
         * doAnswer(i -> resultHolder.compareAndSet(null, i.getArgument(1)))
         * .when(request).setAttribute(
         * eq(SERIALIZATION_TEST_REQUEST_ATTRIBUTE_KEY), any());
         */
        response = mock(VaadinResponse.class);

    }

    @Test
    void handleRequest_developmentModeAndSerializationDebugEnabled_serializationAndDeserializationPerformed() {
        httpSession.setAttribute("OBJ1", new SerializableChild());
        httpSession.setAttribute("OBJ2", new SerializableParent());

        runDebugTool();
        SerializationDebugRequestHandler.Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes()).containsExactlyInAnyOrder(
                SerializationDebugRequestHandler.Outcome.SUCCESS);
        assertThat(result.getStorageKey()).isNotNull()
                .contains("_SOURCE:" + httpSession.getId());
    }

    @Test
    void handleRequest_notUIDLrequest_skip() {
        reset(request);
        when(request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER))
                .thenReturn(HandlerHelper.RequestType.INIT.getIdentifier());

        httpSession.setAttribute("OBJ1", new NotSerializable());

        assertDebugToolNotExecuted();
    }

    @Test
    void handleRequest_productionMode_skip() {
        Mockito.reset(appConfig);
        when(appConfig.isProductionMode()).thenReturn(true);
        when(appConfig.isDevModeSessionSerializationEnabled()).thenReturn(true);

        httpSession.setAttribute("OBJ1", new NotSerializable());

        assertDebugToolNotExecuted();
    }

    @Test
    void handleRequest_serializationDebugDisabled_skip() {
        Mockito.reset(appConfig);
        when(appConfig.isProductionMode()).thenReturn(false);
        when(appConfig.isDevModeSessionSerializationEnabled())
                .thenReturn(false);

        httpSession.setAttribute("OBJ1", new NotSerializable());

        assertDebugToolNotExecuted();
    }

    @Test
    void handleRequest_rootObjectNotSerializable_errorReported() {
        httpSession.setAttribute("OBJ1", new NotSerializable());

        runDebugTool();
        SerializationDebugRequestHandler.Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes()).containsExactlyInAnyOrder(
                SerializationDebugRequestHandler.Outcome.SERIALIZATION_FAILED,
                SerializationDebugRequestHandler.Outcome.NOT_SERIALIZABLE_CLASSES);
        assertThat(result.getStorageKey()).isNotNull()
                .contains("_SOURCE:" + httpSession.getId());
    }


    @Test
    void handleRequest_childObjectNotSerializable_errorReported() {
        httpSession.setAttribute("OBJ1", new ChildNotSerializable());

        runDebugTool();
        SerializationDebugRequestHandler.Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes()).containsExactlyInAnyOrder(
                SerializationDebugRequestHandler.Outcome.SERIALIZATION_FAILED,
                SerializationDebugRequestHandler.Outcome.NOT_SERIALIZABLE_CLASSES);
        assertThat(result.getStorageKey()).isNotNull()
                .contains("_SOURCE:" + httpSession.getId());
    }

    @Test
    void handleRequest_rootObjectDeserializationFailure_errorCaught() {
        httpSession.setAttribute("OBJ1", new DeserializationFailure());

        runDebugTool();
        SerializationDebugRequestHandler.Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes()).containsExactlyInAnyOrder(
                SerializationDebugRequestHandler.Outcome.DESERIALIZATION_FAILED);
        assertThat(result.getStorageKey()).isNotNull()
                .contains("_SOURCE:" + httpSession.getId());
    }

    @Test
    void handleRequest_childObjectDeserializationFailure_errorCaught() {
        httpSession.setAttribute("OBJ1", new ChildDeserializationFailure());

        runDebugTool();
        SerializationDebugRequestHandler.Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes()).containsExactlyInAnyOrder(
                SerializationDebugRequestHandler.Outcome.DESERIALIZATION_FAILED);
        assertThat(result.getStorageKey()).isNotNull()
                .contains("_SOURCE:" + httpSession.getId());
    }

    @Test
    @Disabled("Find a way to simulate SerializedLambda ClassCastException")
    void handleRequest_lambdaSelfReferenceClassCast_errorCaught() {

    }

    private void assertDebugToolNotExecuted() {
        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
        assertThat(toolRunner).isNull();
        assertThat(resultHolder.get()).isNull();
    }

    private void runDebugTool() {
        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
        toolRunner.accept(httpRequest);
    }

    private static class SerializableParent implements Serializable {
        private SerializableChild child = new SerializableChild();
    }

    private static class SerializableChild implements Serializable {
        private String data = "TEST";
    }

    private static class NotSerializable {

    }

    private static class ChildNotSerializable implements Serializable {
        private NotSerializable data = new NotSerializable();
    }

    private static class DeserializationFailure implements Serializable {

        private void readObject(ObjectInputStream is) {
            throw new RuntimeException("Simulate deserialization error");
        }
    }

    private static class ChildDeserializationFailure implements Serializable {
        private DeserializationFailure data = new DeserializationFailure();
    }

}
