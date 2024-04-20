package com.vaadin.kubernetes.starter.sessiontracker.serialization.debug;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.data.Index;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
import com.vaadin.kubernetes.starter.SerializationProperties;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.SerializationDebugRequestHandler.Runner;
import com.vaadin.kubernetes.starter.test.EnableOnJavaIOReflection;
import com.vaadin.kubernetes.starter.ui.SessionDebugNotifier;

import static com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.SerializationDebugRequestHandler.SERIALIZATION_TEST_REQUEST_ATTRIBUTE_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@EnableOnJavaIOReflection
@EnabledIfSystemProperty(named = "sun.io.serialization.extendedDebugInfo", matches = "true", disabledReason = "Tests need system property sun.io.serialization.extendedDebugInfo to be enabled")
class SerializationDebugRequestHandlerTest {

    SerializationDebugRequestHandler handler;

    private ApplicationConfiguration appConfig;
    private VaadinSession vaadinSession;
    private VaadinRequest request;
    private VaadinResponse response;
    private AtomicReference<Result> resultHolder;
    private HttpSession httpSession;
    private HttpServletRequest httpRequest;
    private Runner toolRunner;

    @BeforeEach
    void setUp() {
        SerializationProperties serializationProperties = new SerializationProperties();
        serializationProperties.setTimeout(30000);
        handler = new SerializationDebugRequestHandler(serializationProperties);
        httpSession = new MockHttpSession();
        VaadinService vaadinService = mock(VaadinService.class);
        VaadinContext vaadinContext = mock(VaadinContext.class);
        when(vaadinService.getContext()).thenReturn(vaadinContext);

        PushConfiguration pushConfiguration = mock(PushConfiguration.class);
        when(pushConfiguration.getPushMode()).thenReturn(PushMode.AUTOMATIC);
        UI ui = spy(new UI());
        ui.add(new SessionDebugNotifier() {
            @Override
            public void publishResults(Result result) {
                resultHolder.set(result);
            }
        });
        doAnswer(i -> (SerializableConsumer<Result>) resultHolder::set).when(ui)
                .accessLater(any(SerializableConsumer.class),
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
                any(Runner.class));
        httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute(SERIALIZATION_TEST_REQUEST_ATTRIBUTE_KEY))
                .thenReturn(toolRunner);
        when(httpRequest.getSession(false)).thenReturn(httpSession);
        when(httpRequest.isRequestedSessionIdValid()).thenReturn(true);
        resultHolder = new AtomicReference<>();
        response = mock(VaadinResponse.class);
    }

    @Test
    void handleRequest_developmentModeAndSerializationDebugEnabled_serializationAndDeserializationPerformed() {
        httpSession.setAttribute("OBJ1", new SerializableChild());
        httpSession.setAttribute("OBJ2", new SerializableParent());

        runDebugTool();
        Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes())
                .containsExactlyInAnyOrder(Outcome.SUCCESS);
        assertThat(result.getStorageKey()).isNotNull()
                .contains("_SOURCE:" + httpSession.getId());
    }

    @Test
    void handleRequest_notUIDLRequest_skip() {
        reset(request);
        when(request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER))
                .thenReturn(HandlerHelper.RequestType.INIT.getIdentifier());

        httpSession.setAttribute("OBJ1", new NotSerializable());

        assertDebugToolNotExecuted();
    }

    @Test
    void handleRequest_productionMode_skip() {
        reset(appConfig);
        when(appConfig.isProductionMode()).thenReturn(true);
        when(appConfig.isDevModeSessionSerializationEnabled()).thenReturn(true);

        httpSession.setAttribute("OBJ1", new NotSerializable());

        assertDebugToolNotExecuted();
    }

    @Test
    void handleRequest_serializationDebugDisabled_skip() {
        reset(appConfig);
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

        runDebugTool();
        Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes()).containsExactlyInAnyOrder(
                Outcome.SERIALIZATION_FAILED, Outcome.NOT_SERIALIZABLE_CLASSES);
        assertThat(result.getStorageKey()).isNotNull()
                .contains("_SOURCE:" + httpSession.getId());
    }

    @Test
    void handleRequest_rootObjectDeserializationFailure_errorReported() {
        httpSession.setAttribute("OBJ1", new DeserializationFailure());

        runDebugTool();
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

    @Test
    void handleRequest_childObjectDeserializationFailure_errorCaught() {
        httpSession.setAttribute("OBJ1", new ChildDeserializationFailure());

        runDebugTool();
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

    @Test
    void handleRequest_nestedUnserializable_reportsReferencingClasses() {
        httpSession.setAttribute("OBJ1", new DeepNested());

        runDebugTool();
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

        runDebugTool();
        Result result = resultHolder.get();
        assertThat(result.getOutcomes())
                .containsExactly(Outcome.DESERIALIZATION_FAILED);
        String unserializableInfo = String.join(System.lineSeparator(),
                result.getErrors());

        assertThat(unserializableInfo)
                .contains("cannot assign instance of "
                        + SerializableChild.class.getName())
                .contains("field " + ClassCastSimulation.class.getName()
                        + ".child3")
                .contains(
                        "in instance of " + ClassCastSimulation.class.getName())
                .contains("- object (class \""
                        + ClassCastSimulation.class.getName());
    }

    @Test
    void handleRequest_unserializableObjects_replacedAndRestored() {
        // Unserializable objects replaced with NULL must be restored on
        // deserialized to avoid failure with data structure rejecting null
        // values. For example Map does not allow NULL keys
        Map<Object, Object> map = Map.of("K1", "OK", new NotSerializable(),
                "NS VALUE OK");
        httpSession.setAttribute("OBJ1", map);

        runDebugTool();
        Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes()).containsExactlyInAnyOrder(
                Outcome.SERIALIZATION_FAILED, Outcome.NOT_SERIALIZABLE_CLASSES);
        assertThat(result.getStorageKey()).isNotNull()
                .contains("_SOURCE:" + httpSession.getId());
    }

    @Test
    void handleRequest_serializationTimeout_timeoutReported() {
        SerializationProperties properties = new SerializationProperties();
        properties.setTimeout(1);
        handler = new SerializationDebugRequestHandler(properties);

        httpSession.setAttribute("OBJ1", new SlowSerialization());

        runDebugTool();
        Result result = resultHolder.get();
        assertThat(result.getSessionId()).isEqualTo(httpSession.getId());
        assertThat(result.getOutcomes()).containsExactlyInAnyOrder(
                Outcome.NOT_SERIALIZABLE_CLASSES,
                Outcome.SERIALIZATION_TIMEOUT);
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

    private static class SlowSerialization extends DeepNested {
        private void writeObject(ObjectOutputStream out) throws IOException {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            out.defaultWriteObject();
        }
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

        private ClassCastSimulationChild child1 = new ClassCastSimulationChild(
                false);
        private ClassCastSimulationChild child2 = new ClassCastSimulationChild(
                false);
        private ClassCastSimulationChild child3 = new ClassCastSimulationChild(
                true);
        private ClassCastSimulationChild child4 = new ClassCastSimulationChild(
                false);
        private ClassCastSimulationChild child5 = new ClassCastSimulationChild(
                true);
        private ClassCastSimulationChild child6 = new ClassCastSimulationChild(
                false);
    }

    private static class ClassCastSimulationChild implements Serializable {

        private boolean fail;

        public ClassCastSimulationChild(boolean fail) {
            this.fail = fail;
        }

        private Object readResolve() {
            if (fail) {
                // Force a ClassCastException during deserialization
                return new SerializableChild();
            }
            return this;
        }
    }
}
