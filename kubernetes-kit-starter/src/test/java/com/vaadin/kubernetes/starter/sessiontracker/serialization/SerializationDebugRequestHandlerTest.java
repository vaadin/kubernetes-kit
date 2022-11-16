package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import javax.servlet.http.HttpSession;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.data.Index;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.Outcome;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.Result;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.SerializationDebugRequestHandler;

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
    private AtomicReference<Result> resultHolder;
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
        Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes())
                .containsExactlyInAnyOrder(Outcome.SUCCESS);
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
        Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes()).containsExactlyInAnyOrder(
                Outcome.SERIALIZATION_FAILED, Outcome.NOT_SERIALIZABLE_CLASSES);
        assertThat(result.getStorageKey()).isNotNull()
                .contains("_SOURCE:" + httpSession.getId());
    }

    @Test
    void handleRequest_childObjectNotSerializable_errorReported() {
        httpSession.setAttribute("OBJ1", new ChildNotSerializable());

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
        Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes()).containsExactlyInAnyOrder(
                Outcome.SERIALIZATION_FAILED, Outcome.NOT_SERIALIZABLE_CLASSES);
        assertThat(result.getStorageKey()).isNotNull()
                .contains("_SOURCE:" + httpSession.getId());
    }

    @EnabledIfSystemProperty(named = "sun.io.serialization.extendedDebugInfo", matches = "true")
    @EnabledIf(value = "javaBaseOpenJavaIO", disabledReason = "Cannot reflect on java.io. Use '--add-opens java.base/java.io=ALL-UNNAMED' to enable the test")
    @Test
    void handleRequest_rootObjectDeserializationFailure_errorReported() {
        httpSession.setAttribute("OBJ1", new DeserializationFailure());

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
        Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes())
                .containsExactlyInAnyOrder(Outcome.DESERIALIZATION_FAILED);
        assertThat(result.getStorageKey()).isNotNull()
                .contains("_SOURCE:" + httpSession.getId());
        assertThat(result.getErrors()).satisfies(
                log -> assertThat(log).contains(
                        "- custom writeObject data (class \"java.util.HashMap\")")
                        .contains("- root object")
                        .contains("OBJ1="
                                + DeserializationFailure.class.getName()),
                Index.atIndex(1));
    }

    @EnabledIfSystemProperty(named = "sun.io.serialization.extendedDebugInfo", matches = "true")
    @EnabledIf(value = "javaBaseOpenJavaIO", disabledReason = "Cannot reflect on java.io. Use '--add-opens java.base/java.io=ALL-UNNAMED' to enable the test")
    @Test
    void handleRequest_childObjectDeserializationFailure_errorCaught() {
        httpSession.setAttribute("OBJ1", new ChildDeserializationFailure());

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
        Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes())
                .containsExactlyInAnyOrder(Outcome.DESERIALIZATION_FAILED);
        assertThat(result.getStorageKey()).isNotNull()
                .contains("_SOURCE:" + httpSession.getId());
        assertThat(result.getErrors())
                .overridingErrorMessage("Errors should report failures for "
                        + ChildDeserializationFailure.class.getName())
                .anyMatch(log -> log.contains("- field (class \""
                        + ChildDeserializationFailure.class.getName()));
    }

    private boolean javaBaseOpenJavaIO() {
        return ObjectInputStream.class.getModule().isOpen("java.io",
                getClass().getModule());
    }

    @EnabledIfSystemProperty(named = "sun.io.serialization.extendedDebugInfo", matches = "true")
    @EnabledIf(value = "javaBaseOpenJavaIO", disabledReason = "Cannot reflect on java.io. Use '--add-opens java.base/java.io=ALL-UNNAMED' to enable the test")
    @Test
    void handleRequest_nestedUnserializable_reportsReferencingClasses() {
        httpSession.setAttribute("OBJ1", new DeepNested());

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
        Result result = resultHolder.get();
        assertThat(result.getOutcomes()).containsExactlyInAnyOrder(
                Outcome.SERIALIZATION_FAILED, Outcome.NOT_SERIALIZABLE_CLASSES);
        List<String> unserializableInfo = result.getNotSerializableClasses();
        assertThat(unserializableInfo).element(0)
                .isEqualTo(NotSerializable.class.getName());

        SoftAssertions softAssertions = new SoftAssertions();
        Stream.of(ChildNotSerializable.class.getName(),
                DeepNested.Inner.class.getName(),
                DeepNested.StaticInner.class.getName())
                .forEach(entry -> softAssertions.assertThat(unserializableInfo)
                        .anyMatch(log -> log
                                .contains("- field (class \"" + entry)));
        softAssertions.assertAll();
    }

    @Test
    void handleRequest_deserializationClassCastException_reportsReferencingClasses() {
        httpSession.setAttribute("OBJ1", new ClassCastSimulation());

        assertThat(handler.handleRequest(vaadinSession, request, response))
                .isFalse();
        Result result = resultHolder.get();
        assertThat(result.getOutcomes())
                .containsExactly(Outcome.DESERIALIZATION_FAILED);
        List<String> unserializableInfo = result.getErrors();
        SoftAssertions softAssertions = new SoftAssertions();
        Stream.of(ClassCastSimulation.class.getName()).forEach(
                entry -> softAssertions.assertThat(unserializableInfo).anyMatch(
                        log -> log.contains("- field (class \"" + entry)));
        softAssertions.assertAll();
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

    private static class DeepNested implements Serializable {

        private final ChildNotSerializable root = new ChildNotSerializable();
        private final Inner inner = new Inner();
        private final StaticInner staticInner = new StaticInner();
        private final List<Object> collection = new ArrayList<>(
                List.of(new Inner(), new StaticInner(),
                        new ChildNotSerializable(), new NotSerializable()));

        class Inner implements Serializable {
            private NotSerializable staticInner = new NotSerializable();
        }

        static class StaticInner implements Serializable {
            private NotSerializable staticInner1 = new NotSerializable();

            private NotSerializable staticInner2 = null;

            private NotSerializable staticInner3 = new NotSerializable();
        }

    }

    private static class DeserializationFailure implements Serializable {

        private void readObject(ObjectInputStream is) {
            throw new RuntimeException("Simulate deserialization error");
        }
    }

    private static class ChildDeserializationFailure implements Serializable {
        private DeserializationFailure data = new DeserializationFailure();
    }

    private static class ClassCastSimulation implements Serializable {

        private ClassCastSimulationChild child = new ClassCastSimulationChild();
    }

    private static class ClassCastSimulationChild implements Serializable {

        private Object readResolve() {
            // Force a ClassCastException during deserialiation
            return new SerializableChild();
        }
    }

}
