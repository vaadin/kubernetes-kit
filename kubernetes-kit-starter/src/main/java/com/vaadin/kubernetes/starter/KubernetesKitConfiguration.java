/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter;

import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.Predicate;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.util.StringUtils;

import com.vaadin.flow.spring.SpringBootAutoConfiguration;
import com.vaadin.kubernetes.starter.sessiontracker.SessionListener;
import com.vaadin.kubernetes.starter.sessiontracker.SessionSerializationCallback;
import com.vaadin.kubernetes.starter.sessiontracker.SessionSerializer;
import com.vaadin.kubernetes.starter.sessiontracker.SessionTrackerFilter;
import com.vaadin.kubernetes.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.kubernetes.starter.sessiontracker.backend.HazelcastConnector;
import com.vaadin.kubernetes.starter.sessiontracker.backend.RedisConnector;
import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionExpirationPolicy;
import com.vaadin.kubernetes.starter.sessiontracker.push.PushSessionTracker;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.SerializationStreamFactory;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.SpringTransientHandler;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientHandler;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientInjectableObjectStreamFactory;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.DebugMode;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.SerializationDebugRequestHandler;

/**
 * This configuration bean is provided to autoconfigure Vaadin apps to run in a
 * clustered environment.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "auto-configure", prefix = KubernetesKitProperties.PREFIX, matchIfMissing = true)
@AutoConfigureAfter(SpringBootAutoConfiguration.class)
@EnableConfigurationProperties({ KubernetesKitProperties.class,
        SerializationProperties.class })
public class KubernetesKitConfiguration {

    @AutoConfiguration
    @ConditionalOnBean(BackendConnector.class)
    public static class VaadinReplicatedSessionConfiguration {

        public static final String TRANSIENT_INJECTABLE_FILTER = "vaadinSerializationTransientInjectableFilter";

        private static final Predicate<Class<?>> TRANSIENT_INJECTABLE_VAADIN_EXCLUSIONS = type -> !type
                .getPackageName().startsWith("com.vaadin.flow.internal");

        final KubernetesKitProperties kubernetesKitProperties;

        public VaadinReplicatedSessionConfiguration(
                KubernetesKitProperties kubernetesKitProperties) {
            this.kubernetesKitProperties = kubernetesKitProperties;
        }

        SessionTrackerFilter sessionTrackerFilter(
                SessionSerializer sessionSerializer,
                SessionListener sessionListener) {
            return new SessionTrackerFilter(sessionSerializer,
                    kubernetesKitProperties, sessionListener);
        }

        SessionListener sessionListener(BackendConnector backendConnector,
                SessionSerializer sessionSerializer) {
            return new SessionListener(backendConnector, sessionSerializer);
        }

        @Bean
        @ConditionalOnMissingBean
        TransientHandler springDeserializationHandler(
                ApplicationContext appCtx) {
            return new SpringTransientHandler(appCtx);
        }

        @Bean(TRANSIENT_INJECTABLE_FILTER)
        @ConditionalOnMissingBean
        Predicate<Class<?>> transientInjectableFilter(
                SerializationProperties props) {
            return props.getTransients().transientInjectableFilter();
        }

        @Bean
        @ConditionalOnMissingBean
        SessionExpirationPolicy sessionExpirationPolicy() {
            Duration duration = kubernetesKitProperties
                    .getBackendSessionExpirationTolerance();
            if (duration != null) {
                return sessionTimeout -> duration.plus(sessionTimeout,
                        ChronoUnit.SECONDS);
            }
            return SessionExpirationPolicy.NEVER;
        }

        @Bean
        @ConditionalOnMissingBean
        SessionSerializationCallback sessionSerializationCallback() {
            return SessionSerializationCallback.DEFAULT;
        }

        @Bean
        @ConditionalOnMissingBean
        SerializationStreamFactory serializationStreamFactory() {
            return new TransientInjectableObjectStreamFactory();
        }

        @Bean
        SessionSerializer sessionSerializer(BackendConnector backendConnector,
                TransientHandler transientInjector,
                SessionSerializationCallback sessionSerializationCallback,
                SessionExpirationPolicy sessionExpirationPolicy,
                @Autowired(required = false) @Qualifier(TRANSIENT_INJECTABLE_FILTER) Predicate<Class<?>> injectablesFilter,
                SerializationStreamFactory serializationStreamFactory,
                SerializationProperties serializationProperties) {
            SessionSerializer sessionSerializer = new SessionSerializer(
                    backendConnector, transientInjector,
                    sessionExpirationPolicy, sessionSerializationCallback,
                    serializationStreamFactory, serializationProperties);
            if (injectablesFilter != null) {
                sessionSerializer.setInjectableFilter(injectablesFilter);
            }
            return sessionSerializer;
        }

        /**
         * Gets a composed transient injectable filter that rejects Vaadin
         * internal classes that should not be inspected and may break
         * serialization process due to Java accessibility rules.
         *
         * @param injectablesFilter
         *            a predicate that will be logically-ANDed with this
         *            predicate
         * @return a composed predicate that represents the short-circuiting
         *         logical AND of Vaadin default predicate and the other
         *         predicate
         */
        public static Predicate<Class<?>> withVaadinDefaultFilter(
                Predicate<Class<?>> injectablesFilter) {
            // Some Vaadin classes should be ignored by default to avoid
            // reflection errors with Java 17
            // For example NodeMap$HashMapValues that extends HashMap will fail
            // because of InaccessibleObjectException when getting values of
            // inherited transient field 'table'
            Predicate<Class<?>> filter = TRANSIENT_INJECTABLE_VAADIN_EXCLUSIONS;
            if (injectablesFilter != null) {
                filter = filter.and(injectablesFilter);
            }
            return filter;
        }

        @Bean
        @Order(Integer.MIN_VALUE + 50)
        FilterRegistrationBean<SessionTrackerFilter> sessionTrackerFilterRegistration(
                BackendConnector backendConnector,
                SessionSerializer sessionSerializer,
                PushSessionTracker pushSessionTracker) {
            SessionListener sessionListener = sessionListener(backendConnector,
                    sessionSerializer);
            pushSessionTracker.setActiveSessionChecker(
                    sessionListener.activeSessionChecker());
            FilterRegistrationBean<SessionTrackerFilter> registration = new FilterRegistrationBean<>(
                    sessionTrackerFilter(sessionSerializer,
                            // sessionListener::stop)) {
                            sessionListener)) {
                @Override
                protected FilterRegistration.Dynamic addRegistration(
                        String description, ServletContext servletContext) {
                    servletContext.addListener(sessionListener);
                    return super.addRegistration(description, servletContext);
                }
            };
            registration.setAsyncSupported(true);
            registration.setOrder(Integer.MIN_VALUE + 50);
            return registration;
        }

        @Bean
        PushSessionTracker pushSendListener(SessionSerializer sessionSerializer,
                KubernetesKitProperties properties) {
            return new PushSessionTracker(sessionSerializer,
                    properties.getClusterKeyCookieName());
        }

    }

    @AutoConfiguration
    @Conditional(VaadinReplicatedSessionDevModeConfiguration.OnSessionSerializationDebug.class)
    public static class VaadinReplicatedSessionDevModeConfiguration {

        @Bean
        @ConditionalOnMissingBean
        SerializationDebugRequestHandler.InitListener sessionSerializationDebugToolInstaller(
                SerializationProperties serializationProperties) {
            return new SerializationDebugRequestHandler.InitListener(
                    serializationProperties);
        }

        @Bean
        @Order(Integer.MIN_VALUE)
        FilterRegistrationBean<SerializationDebugRequestHandler.Filter> sessionSerializationDebugToolFilter() {
            return new FilterRegistrationBean<>(
                    new SerializationDebugRequestHandler.Filter());
        }

        private static class OnSessionSerializationDebug
                extends AllNestedConditions {

            public OnSessionSerializationDebug() {
                super(ConfigurationPhase.PARSE_CONFIGURATION);
            }

            @ConditionalOnProperty(prefix = "vaadin", name = "productionMode", havingValue = "false", matchIfMissing = true)
            static class OnDevelopmentMode {

            }

            @ConditionalOnProperty(prefix = "vaadin", name = "devmode.sessionSerialization.enabled")
            static class OnSessionSerialization {

            }

            @Conditional(SerializationTrackingCondition.class)
            private static class SerializationTrackingCondition
                    implements Condition {

                @Override
                public boolean matches(ConditionContext context,
                        AnnotatedTypeMetadata metadata) {
                    return DebugMode
                            .isTrackingAvailable(LoggerFactory.getLogger(
                                    VaadinReplicatedSessionDevModeConfiguration.class));
                }
            }

        }

    }

    @AutoConfiguration(after = DataRedisAutoConfiguration.class)
    @ConditionalOnClass(DataRedisAutoConfiguration.class)
    public static class RedisConfiguration {

        @Bean
        @ConditionalOnBean(RedisConnectionFactory.class)
        @ConditionalOnMissingBean
        RedisConnector redisConnector(RedisConnectionFactory factory) {
            return new RedisConnector(factory);
        }
    }

    @AutoConfiguration
    @ConditionalOnClass(HazelcastInstance.class)
    public static class HazelcastConfiguration {

        final KubernetesKitProperties properties;

        public HazelcastConfiguration(KubernetesKitProperties properties) {
            this.properties = properties;
        }

        @Bean
        @ConditionalOnMissingBean
        HazelcastConnector hazelcastConnector(
                HazelcastInstance hazelcastInstance) {
            return new HazelcastConnector(hazelcastInstance);
        }

        @Bean
        @ConditionalOnMissingBean
        public HazelcastInstance hazelcastInstance() {
            final var config = new Config();

            configure(config);
            // Make sure Hazelcast shutdown hook is disabled so that the instance
            // will be stopped after SessionSerializer saved the latest pending state
            config.setProperty("hazelcast.shutdownhook.enabled", "false");
            configureKubernetes(config);

            return createHazelcastInstance(config);
        }

        HazelcastInstance createHazelcastInstance(Config config) {
            return Hazelcast.newHazelcastInstance(config);
        }

        protected void configure(Config config) {
            // Do nothing
        }

        private void configureKubernetes(Config config) {
            final var k8sProperties = properties.getHazelcast();
            final var k8sServiceName = k8sProperties.getServiceName();

            if (StringUtils.hasText(k8sServiceName)) {
                final var networkConfig = config.getNetworkConfig().getJoin();
                networkConfig.getTcpIpConfig().setEnabled(false);
                networkConfig.getMulticastConfig().setEnabled(false);

                final var k8sConfig = networkConfig.getKubernetesConfig();
                k8sConfig.setEnabled(true);

                final var k8sNamespace = k8sProperties.getNamespace();
                k8sConfig.setProperty("namespace", k8sNamespace);
                k8sConfig.setProperty("service-name", k8sServiceName);
                k8sConfig.setProperty("service-port",
                        Integer.toString(k8sProperties.getServicePort()));
            }
        }

    }
}
