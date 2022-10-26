package com.vaadin.azure.starter.sessiontracker.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        new TransientInjectableObjectOutputStream(os, handler)
                .writeWithTransients(target);

        Object result = new TransientInjectableObjectInputStream(
                new ByteArrayInputStream(os.toByteArray()), handler)
                        .readWithTransients();
        Assertions.assertThat(result).isNotSameAs(target)
                .isExactlyInstanceOf(TestConfig.CtorInjectionTarget.class)
                .hasNoNullFieldsOrProperties()
                .asInstanceOf(InstanceOfAssertFactories
                        .type(TestConfig.CtorInjectionTarget.class))
                .extracting(obj -> obj.defaultImpl, obj -> obj.alternative)
                .containsExactly(target.defaultImpl, target.alternative);
    }

    @Test
    void processComponentWithNullTransients() throws Exception {
        TestConfig.NullTransient target = new TestConfig.NullTransient();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new TransientInjectableObjectOutputStream(os, handler)
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
        new TransientInjectableObjectOutputStream(os, handler, type -> false)
                .writeWithTransients(target);

        Object result = new TransientInjectableObjectInputStream(
                new ByteArrayInputStream(os.toByteArray()), handler)
                        .readWithTransients();
        Assertions.assertThat(result).isNotSameAs(target)
                .isExactlyInstanceOf(TestConfig.CtorInjectionTarget.class)
                .hasAllNullFieldsOrProperties();
    }

}
