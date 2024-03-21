package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import jakarta.activation.MimeType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
