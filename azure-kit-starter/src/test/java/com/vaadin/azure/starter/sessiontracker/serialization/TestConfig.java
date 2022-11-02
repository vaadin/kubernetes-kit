package com.vaadin.azure.starter.sessiontracker.serialization;

import java.io.Serializable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@Import({ TestConfig.CtorInjectionTarget.class,
        TestConfig.PrototypeTarget.class })
class TestConfig {

    @Bean
    @Primary
    TestService defaultImpl() {
        return new ServiceImpl();
    }

    @Bean
    @Qualifier("ALTERNATIVE")
    TestService alternativeImpl() {
        return new AlternativeImpl();
    }

    @Bean
    @Qualifier("TRANSACTIONAL")
    @Transactional
    TestService transactionalService() {
        return new TestService() {
            @Override
            public void execute() {

            }
        };
    }

    static class ServiceImpl implements TestService {
        @Override
        public void execute() {

        }
    }

    static class AlternativeImpl implements TestService {
        @Override
        public void execute() {

        }
    }

    @Component
    static class CtorInjectionTarget implements Serializable {
        transient TestService defaultImpl;
        transient TestService alternative;

        public CtorInjectionTarget(TestService defaultImpl,
                @Qualifier("ALTERNATIVE") TestService alternative) {
            this.defaultImpl = defaultImpl;
            this.alternative = alternative;
        }
    }

    @Component
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @Primary
    static class PrototypeComponent {

    }

    @Component
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @Qualifier("EXTENSION")
    static class PrototypeComponentExt extends PrototypeComponent {

    }

    @Component
    static class PrototypeTarget implements Serializable {

        @Autowired
        transient PrototypeComponent prototypeScoped;
        @Autowired
        @Qualifier("EXTENSION")
        transient PrototypeComponent extPrototypeScoped;
    }

    interface PrototypeService {
    }

    @Component
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @Qualifier("A")
    static class PrototypeServiceImplA implements PrototypeService {

    }

    @Component
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @Qualifier("B")
    static class PrototypeServiceImplB implements PrototypeService {

    }

    @Component
    static class PrototypeServiceTarget implements Serializable {

        @Autowired
        @Qualifier("A")
        transient PrototypeService prototypeServiceA;
        @Autowired
        @Qualifier("B")
        transient PrototypeService prototypeServiceB;
    }

    @Component
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE, proxyMode = ScopedProxyMode.TARGET_CLASS)
    @Qualifier("A-PROXY")
    static class ProxiedPrototypeServiceImplA implements PrototypeService {
        public void action() {
        }
    }

    @Component
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE, proxyMode = ScopedProxyMode.INTERFACES)
    @Qualifier("B-PROXY")
    static class ProxiedPrototypeServiceImplB implements PrototypeService {

        public void action() {
        }

    }

    @Component
    static class ProxiedPrototypeServiceTarget implements Serializable {

        @Autowired
        @Qualifier("A-PROXY")
        transient PrototypeService prototypeServiceA;
        @Autowired
        @Qualifier("B-PROXY")
        transient PrototypeService prototypeServiceB;
    }

    static class NullTransient implements Serializable {

        transient TestService notInitialized;
    }

    @Component(NamedComponent.NAME)
    static class NamedComponent {
        static final String NAME = "__@Component_withName";
    }

    @Component
    static class NamedComponentTarget implements Serializable {

        @Autowired
        transient NamedComponent named;
    }

    @Component
    static class NotInjected implements Serializable {

        transient TestService notInjected = new ServiceImpl();
    }

    @Component
    static class ProxiedBeanTarget implements Serializable {
        @Autowired
        @Qualifier("TRANSACTIONAL")
        transient TestService service;
    }
}
