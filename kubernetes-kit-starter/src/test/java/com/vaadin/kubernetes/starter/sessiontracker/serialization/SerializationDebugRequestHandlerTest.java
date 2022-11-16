package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import javax.servlet.http.HttpSession;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpSession;

import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedHttpSession;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import com.vaadin.flow.shared.ApplicationConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

class SerializationDebugRequestHandlerTest {

    SerializationDebugRequestHandler handler;

    private ApplicationConfiguration appConfig;
    private VaadinSession vaadinSession;
    private VaadinRequest request;
    private VaadinResponse response;
    private AtomicReference<SerializationDebugRequestHandler.Result> resultHolder;
    private HttpSession httpSession;

    @BeforeEach
    void setUp() {
        handler = new SerializationDebugRequestHandler();
        httpSession = new MockHttpSession();
        VaadinService vaadinService = mock(VaadinService.class);
        VaadinContext vaadinContext = mock(VaadinContext.class);
        when(vaadinService.getContext()).thenReturn(vaadinContext);

        appConfig = mock(ApplicationConfiguration.class);
        when(vaadinContext.getAttribute(eq(ApplicationConfiguration.class),
                any())).thenReturn(appConfig);
        when(appConfig.isProductionMode()).thenReturn(false);
        when(appConfig.isDevModeSessionSerializationEnabled()).thenReturn(true);

        vaadinSession = mock(VaadinSession.class);
        when(vaadinSession.getService()).thenReturn(vaadinService);
        when(vaadinSession.getSession())
                .thenReturn(new WrappedHttpSession(httpSession));
        request = mock(VaadinRequest.class);
        when(request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER))
                .thenReturn(HandlerHelper.RequestType.UIDL.getIdentifier());
        resultHolder = new AtomicReference<>();
        doAnswer(i -> resultHolder.compareAndSet(null, i.getArgument(1)))
                .when(request)
                .setAttribute(eq(
                        SerializationDebugRequestHandler.SERIALIZATION_TEST_REQUEST_ATTRIBUTE_KEY),
                        any());
        response = mock(VaadinResponse.class);

    }

    @Test
    void handleRequest_developmentModeAndSerializationDebugEnabled_serializationAndDeserializationPerformed() {
        httpSession.setAttribute("OBJ1", new SerializableChild());
        httpSession.setAttribute("OBJ2", new SerializableParent());

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
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

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
        assertThat(resultHolder.get()).isNull();
    }

    @Test
    void handleRequest_productionMode_skip() {
        Mockito.reset(appConfig);
        when(appConfig.isProductionMode()).thenReturn(true);
        when(appConfig.isDevModeSessionSerializationEnabled()).thenReturn(true);

        httpSession.setAttribute("OBJ1", new NotSerializable());

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
        assertThat(resultHolder.get()).isNull();
    }

    @Test
    void handleRequest_serializationDebugDisabled_skip() {
        Mockito.reset(appConfig);
        when(appConfig.isProductionMode()).thenReturn(false);
        when(appConfig.isDevModeSessionSerializationEnabled())
                .thenReturn(false);

        httpSession.setAttribute("OBJ1", new NotSerializable());

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
        assertThat(resultHolder.get()).isNull();
    }

    @Test
    void handleRequest_rootObjectNotSerializable_errorReported() {
        httpSession.setAttribute("OBJ1", new NotSerializable());

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
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

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
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

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
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

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
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
