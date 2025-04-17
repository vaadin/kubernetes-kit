package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import jakarta.activation.MimeType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.kubernetes.starter.sessiontracker.UnserializableComponentWrapper;
import com.vaadin.kubernetes.starter.sessiontracker.UnserializableComponentWrapperFoundException;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.DebugMode;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.Track;
import com.vaadin.kubernetes.starter.test.EnableOnJavaIOReflection;
import com.vaadin.testbench.unit.mocks.MockedUI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = TestConfig.class)
@ExtendWith(SpringExtension.class)
class SerializationDeserializationTest {

    @Autowired
    ApplicationContext appCtx;

    SpringTransientHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SpringTransientHandler(appCtx);
    }

    @Test
    void processConstructorInjectedComponent(
            @Autowired TestConfig.CtorInjectionTarget target) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        TransientInjectableObjectOutputStream.newInstance(os, handler)
                .writeWithTransients(target);

        Object result = new TransientInjectableObjectInputStream(
                new ByteArrayInputStream(os.toByteArray()), handler)
                .readWithTransients();
        Assertions.assertThat(result).isNotSameAs(target)
                .isExactlyInstanceOf(TestConfig.CtorInjectionTarget.class)
                .asInstanceOf(InstanceOfAssertFactories
                        .type(TestConfig.CtorInjectionTarget.class))
                .extracting(obj -> obj.defaultImpl, obj -> obj.alternative)
                .containsExactly(target.defaultImpl, target.alternative);
    }

    @Test
    void processComponentWithNullTransients() throws Exception {
        TestConfig.NullTransient target = new TestConfig.NullTransient();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        TransientInjectableObjectOutputStream.newInstance(os, handler)
                .writeWithTransients(target);

        Object result = new TransientInjectableObjectInputStream(
                new ByteArrayInputStream(os.toByteArray()), handler)
                .readWithTransients();
        Assertions.assertThat(result).isNotSameAs(target)
                .isExactlyInstanceOf(TestConfig.NullTransient.class)
                .hasFieldOrPropertyWithValue("notInitialized", null);
    }

    @Test
    void detachedUnserializableWrapper_nullVaadinSession_throws()
            throws Exception {
        TestConfig.UnserializableWrapper wrapper = new TestConfig.UnserializableWrapper(
                new TestConfig.Unserializable("Unserializable"));

        MockedUI ui = new MockedUI();
        ComponentUtil.setData(ui, "WRAPPER", wrapper);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Assertions
                .assertThatExceptionOfType(
                        UnserializableComponentWrapperFoundException.class)
                .isThrownBy(() -> TransientInjectableObjectOutputStream
                        .newInstance(os, handler).writeWithTransients(ui));
    }

    @Test
    void detachedUnserializableWrapper_unlockedVaadinSession_throws()
            throws Exception {
        TestConfig.UnserializableWrapper wrapper = new TestConfig.UnserializableWrapper(
                new TestConfig.Unserializable("Unserializable"));

        MockedUI ui = new MockedUI();
        VaadinSession session = mock(VaadinSession.class);
        when(session.hasLock()).thenReturn(false);
        ui.getInternals().setSession(session);
        ComponentUtil.setData(ui, "WRAPPER", wrapper);

        CurrentInstance.clearAll();
        VaadinSession.setCurrent(session);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            Assertions
                    .assertThatExceptionOfType(
                            UnserializableComponentWrapperFoundException.class)
                    .isThrownBy(() -> TransientInjectableObjectOutputStream
                            .newInstance(os, handler).writeWithTransients(ui));
        } finally {
            CurrentInstance.clearAll();
        }
    }

    @Test
    void detachedUnserializableWrapper_lockedVaadinSession_handlesUnserializable()
            throws Exception {
        TestConfig.UnserializableWrapper wrapper = new TestConfig.UnserializableWrapper(
                new TestConfig.Unserializable("Unserializable"));

        MockedUI ui = new MockedUI();
        VaadinSession session = mock(VaadinSession.class);
        ReflectionTestUtils.setField(session, "requestHandlers",
                new LinkedList<>());
        ReflectionTestUtils.setField(session, "destroyListeners",
                new CopyOnWriteArrayList<>());
        when(session.hasLock()).thenReturn(true);
        ui.getInternals().setSession(session);
        ComponentUtil.setData(ui, "WRAPPER", wrapper);

        CurrentInstance.clearAll();
        VaadinSession.setCurrent(session);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (var mockedStatic = mockStatic(
                UnserializableComponentWrapper.class)) {
            TransientInjectableObjectOutputStream.newInstance(os, handler)
                    .writeWithTransients(ui);
            mockedStatic.verify(() -> UnserializableComponentWrapper
                    .beforeSerialization(wrapper));
            mockedStatic.verify(() -> UnserializableComponentWrapper
                    .afterSerialization(wrapper));
        } finally {
            CurrentInstance.clearAll();
        }
    }

    @Test
    void serialization_filterInjectables_componentIgnored(
            @Autowired TestConfig.CtorInjectionTarget target) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        TransientInjectableObjectOutputStream
                .newInstance(os, handler, type -> false)
                .writeWithTransients(target);

        Object result = new TransientInjectableObjectInputStream(
                new ByteArrayInputStream(os.toByteArray()), handler)
                .readWithTransients();
        Assertions.assertThat(result).isNotSameAs(target)
                .isExactlyInstanceOf(TestConfig.CtorInjectionTarget.class)
                .hasAllNullFieldsOrProperties();
    }

    @Test
    void serialization_defaultInjectableFilter_componentIgnored(
            @Autowired TestConfig.CtorInjectionTarget obj) throws Exception {
        List<Object> target = new ArrayList<>();
        target.add(new HashMap<>());
        target.add(obj);
        target.add(new MimeType());

        TransientHandler mockHandler = Mockito.mock(TransientHandler.class);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        TransientInjectableObjectOutputStream.newInstance(os, mockHandler)
                .writeWithTransients(target);

        Mockito.verify(mockHandler).inspect(obj);
        Mockito.verifyNoMoreInteractions(mockHandler);
    }

    @Test
    @EnableOnJavaIOReflection
    void serialization_transientInspection_trackObjectsIgnored(
            @Autowired TestConfig.CtorInjectionTarget obj) throws Exception {
        List<Object> target = new ArrayList<>();
        target.add(new HashMap<>());
        target.add(obj);
        target.add(new MimeType());

        TransientHandler mockHandler = Mockito.mock(TransientHandler.class,
                Mockito.withSettings().extraInterfaces(DebugMode.class));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        TransientInjectableObjectOutputStream.newInstance(os, mockHandler)
                .writeWithTransients(target);

        Mockito.verify(mockHandler, Mockito.never())
                .inspect(ArgumentMatchers.argThat(o -> o instanceof Track));
    }

    @Test
    void defaultInspectionFilter_rejectJavaClasses() {
        Pattern pattern = TransientInjectableObjectOutputStream.INSPECTION_REJECTION_PATTERN;
        Assertions.assertThat(ArrayList.class.getPackageName())
                .matches(pattern);
        Assertions.assertThat(MimeType.class.getPackageName()).matches(pattern);
        Assertions.assertThat("sun.misc.Unsafe").matches(pattern); // NOSONAR
        Assertions.assertThat("com.sun.security.auth.LdapPrincipal")
                .matches(pattern); // NOSONAR
    }
}
