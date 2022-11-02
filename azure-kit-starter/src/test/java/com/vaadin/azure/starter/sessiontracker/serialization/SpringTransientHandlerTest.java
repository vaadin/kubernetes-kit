package com.vaadin.azure.starter.sessiontracker.serialization;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ContextConfiguration(classes = { TestConfig.class })
@ExtendWith(SpringExtension.class)
class SpringTransientHandlerTest {

    @Autowired
    ApplicationContext appCtx;
    SpringTransientHandler handler;

    @Autowired
    TestConfig.CtorInjectionTarget target;

    @BeforeEach
    void setUp() {
        handler = new SpringTransientHandler(appCtx);
    }

    @Test
    void inspect_byTypeAndQualifier_beansAreDetected() {
        List<TransientDescriptor> transients = handler.inspect(target);

        Map<String, Object> beans = transients.stream()
                .collect(Collectors.toMap(TransientDescriptor::getName,
                        descr -> appCtx.getBean(descr.getInstanceReference())));
        assertThat(beans.keySet()).containsExactlyInAnyOrder("defaultImpl",
                "alternative");

        Assertions.assertSame(target.defaultImpl, beans.get("defaultImpl"));
        Assertions.assertSame(target.alternative, beans.get("alternative"));
    }

    @Test
    void inspect_inheritedFields_beansAreDetected() {
        Child bean = appCtx.getAutowireCapableBeanFactory()
                .createBean(Child.class);
        List<TransientDescriptor> transients = handler.inspect(bean);

        assertThat(transients).hasSize(2).containsExactlyInAnyOrder(
                new TransientDescriptor(Parent.class, "theService",
                        TestService.class, "alternativeImpl"),
                new TransientDescriptor(Child.class, "theService",
                        TestService.class, "defaultImpl"));
    }

    @Test
    void inspect_byName_beansAreDetected(
            @Autowired TestConfig.NamedComponentTarget target) {
        List<TransientDescriptor> transients = handler.inspect(target);

        assertThat(transients).containsExactlyInAnyOrder(
                new TransientDescriptor(TestConfig.NamedComponentTarget.class,
                        "named", TestConfig.NamedComponent.class,
                        TestConfig.NamedComponent.NAME));

    }

    @Test
    void inspect_prototypeScopedBeansWithInheritance_beansAreDetected(
            @Autowired TestConfig.PrototypeTarget target) {
        List<TransientDescriptor> transients = handler.inspect(target);

        assertThat(transients).containsExactlyInAnyOrder(
                new TransientDescriptor(TestConfig.PrototypeTarget.class,
                        "prototypeScoped", TestConfig.PrototypeComponent.class,
                        TestConfig.PrototypeComponent.class.getName()),
                new TransientDescriptor(TestConfig.PrototypeTarget.class,
                        "extPrototypeScoped",
                        TestConfig.PrototypeComponent.class,
                        TestConfig.PrototypeComponentExt.class.getName()));
    }

    @Test
    void inspect_prototypeScopedBeans_beansAreDetected(
            @Autowired TestConfig.PrototypeServiceTarget target) {
        List<TransientDescriptor> transients = handler.inspect(target);

        assertThat(transients).containsExactlyInAnyOrder(
                new TransientDescriptor(TestConfig.PrototypeServiceTarget.class,
                        "prototypeServiceA", TestConfig.PrototypeService.class,
                        TestConfig.PrototypeServiceImplA.class.getName()),
                new TransientDescriptor(TestConfig.PrototypeServiceTarget.class,
                        "prototypeServiceB", TestConfig.PrototypeService.class,
                        TestConfig.PrototypeServiceImplB.class.getName()));
    }

    @Test
    void inspect_proxiedPrototypeScopedBeans_beansAreDetected(
            @Autowired TestConfig.ProxiedPrototypeServiceTarget target) {
        List<TransientDescriptor> transients = handler.inspect(target);

        assertThat(transients).containsExactlyInAnyOrder(
                new TransientDescriptor(
                        TestConfig.ProxiedPrototypeServiceTarget.class,
                        "prototypeServiceA", TestConfig.PrototypeService.class,
                        TestConfig.ProxiedPrototypeServiceImplA.class
                                .getName()),
                new TransientDescriptor(
                        TestConfig.ProxiedPrototypeServiceTarget.class,
                        "prototypeServiceB", TestConfig.PrototypeService.class,
                        TestConfig.ProxiedPrototypeServiceImplB.class
                                .getName()));
    }

    @Test
    void inspect_proxiedBeans_beansAreDetected(
            @Autowired TestConfig.ProxiedBeanTarget target) {
        List<TransientDescriptor> transients = handler.inspect(target);

        assertThat(transients).containsExactlyInAnyOrder(
                new TransientDescriptor(TestConfig.ProxiedBeanTarget.class,
                        "service", TestService.class, "transactionalService"));
    }

    @Test
    void inspect_notInjected_fieldIsIgnored(
            @Autowired TestConfig.NotInjected target) {
        List<TransientDescriptor> transients = handler.inspect(target);

        assertThat(transients).isEmpty();
    }

    @Test
    void inject_detectedBeansAreInjected() {
        TestConfig.CtorInjectionTarget newTarget = new TestConfig.CtorInjectionTarget(
                null, null);
        List<TransientDescriptor> descriptors = List.of(
                new TransientDescriptor(TestConfig.CtorInjectionTarget.class,
                        "defaultImpl", TestService.class, "defaultImpl"),
                new TransientDescriptor(TestConfig.CtorInjectionTarget.class,
                        "alternative", TestService.class, "alternativeImpl"));
        handler.inject(newTarget, descriptors);

        Assertions.assertSame(target.defaultImpl, newTarget.defaultImpl,
                "default impl");
        Assertions.assertSame(target.alternative, newTarget.alternative,
                "alternative impl");
    }

    static class Parent {
        @Autowired
        @Qualifier("ALTERNATIVE")
        private transient TestService theService;
    }

    static class Child extends Parent {
        @Autowired
        private transient TestService theService;
    }

}