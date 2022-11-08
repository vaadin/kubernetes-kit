package com.vaadin.azure.starter;

import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import java.util.function.Predicate;

import com.hazelcast.config.AttributeConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.IndexType;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.nio.serialization.Serializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.hazelcast.Hazelcast4IndexedSessionRepository;
import org.springframework.session.hazelcast.Hazelcast4PrincipalNameExtractor;
import org.springframework.session.hazelcast.HazelcastSessionSerializer;
import org.springframework.session.hazelcast.config.annotation.SpringSessionHazelcastInstance;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.util.StringUtils;

import com.vaadin.azure.starter.sessiontracker.SessionListener;
import com.vaadin.azure.starter.sessiontracker.SessionSerializer;
import com.vaadin.azure.starter.sessiontracker.SessionTrackerFilter;
import com.vaadin.azure.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.azure.starter.sessiontracker.backend.HazelcastConnector;
import com.vaadin.azure.starter.sessiontracker.backend.RedisConnector;
import com.vaadin.azure.starter.sessiontracker.push.PushSendListener;
import com.vaadin.azure.starter.sessiontracker.push.PushSessionTracker;
import com.vaadin.azure.starter.sessiontracker.serialization.SerializationDebugRequestHandler;
import com.vaadin.azure.starter.sessiontracker.serialization.SpringTransientHandler;
import com.vaadin.azure.starter.sessiontracker.serialization.TransientHandler;

/**
 * This configuration bean is provided to auto-configure Vaadin apps to run in a
 * clustered environment.
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
@EnableConfigurationProperties({ AzureKitProperties.class,
        SerializationProperties.class })
public class AzureKitConfiguration {

    @AutoConfiguration
    @ConditionalOnMissingClass("org.springframework.session.Session")
    @ConditionalOnBean(BackendConnector.class)
    public static class VaadinReplicatedSessionConfiguration {

        public static final String TRANSIENT_INJECTABLE_FILTER = "vaadinSerializationTransientInjectableFilter";

        private static final Predicate<Class<?>> TRANSIENT_INJECTABLE_VAADIN_EXCLUSIONS = type -> !type
                .getPackageName().startsWith("com.vaadin.flow.internal");

        SessionTrackerFilter sessionTrackerFilter(
                SessionSerializer sessionSerializer) {
            return new SessionTrackerFilter(sessionSerializer);
        }

        SessionListener sessionListener(BackendConnector backendConnector,
                SessionSerializer sessionSerializer) {
            return new SessionListener(backendConnector, sessionSerializer);
        }

        @Bean
        SpringTransientHandler springDeserializationHandler(
                ApplicationContext appCtx) {
            return new SpringTransientHandler(appCtx);
        }

        @Bean(TRANSIENT_INJECTABLE_FILTER)
        @ConditionalOnMissingBean
        Predicate<Class<?>> transientInjectableFilter(
                SerializationProperties props) {
            return props.transientInjectableFilter();
        }

        @Bean
        SessionSerializer sessionSerializer(BackendConnector backendConnector,
                TransientHandler transientInjector,
                @Autowired(required = false) @Qualifier(TRANSIENT_INJECTABLE_FILTER) Predicate<Class<?>> injectablesFilter) {
            SessionSerializer sessionSerializer = new SessionSerializer(
                    backendConnector, transientInjector);
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
                SessionSerializer sessionSerializer) {
            return new FilterRegistrationBean<>(
                    sessionTrackerFilter(sessionSerializer)) {
                @Override
                protected FilterRegistration.Dynamic addRegistration(
                        String description, ServletContext servletContext) {
                    servletContext.addListener(sessionListener(backendConnector,
                            sessionSerializer));
                    return super.addRegistration(description, servletContext);
                }
            };
        }

        @Bean
        PushSendListener pushSendListener(SessionSerializer sessionSerializer) {
            return new PushSessionTracker(sessionSerializer);
        }

    }

    @AutoConfiguration
    @Conditional(VaadinReplicatedSessionDevModeConfiguration.OnSessionSerializationDebug.class)
    public static class VaadinReplicatedSessionDevModeConfiguration {
        @Bean
        @ConditionalOnMissingBean
        SerializationDebugRequestHandler.InitListener sessionSerializationDebugToolInstaller() {
            return new SerializationDebugRequestHandler.InitListener();
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

        }
    }

    @AutoConfiguration
    @ConditionalOnClass(RedisConnectionFactory.class)
    public static class RedisConfiguration {
        @Bean
        @ConditionalOnBean(RedisConnectionFactory.class)
        @ConditionalOnMissingBean
        RedisConnector redisConnector(RedisConnectionFactory factory) {
            return new RedisConnector(factory);
        }
    }

    @AutoConfiguration
    @EnableRedisHttpSession
    @ConditionalOnClass(RedisIndexedSessionRepository.class)
    public static class RedisSessionRepositoryConfiguration {

        static final String BEAN_NAME = "vaadinRedisSessionRepositoryCustomizer";

        /**
         * Provides a {@link SessionRepositoryCustomizer} bean to configure
         * {@link RedisIndexedSessionRepository} to work with Vaadin.
         *
         * @return the session-repository customizer
         */
        @Bean(BEAN_NAME)
        public SessionRepositoryCustomizer<RedisIndexedSessionRepository> redisSessionRepositoryCustomizer() {
            return repository -> {
                repository.setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
            };
        }

    }

    @AutoConfiguration
    @EnableHazelcastHttpSession
    @ConditionalOnClass(Hazelcast4IndexedSessionRepository.class)
    public static class HazelcastSessionRepositoryConfiguration
            extends HazelcastSupport {

        static final String BEAN_NAME = "vaadinHazelcastSessionRepositoryCustomizer";

        public HazelcastSessionRepositoryConfiguration(
                AzureKitProperties properties) {
            super(properties);
        }

        /**
         * Provides a {@link SessionRepositoryCustomizer} bean to configure
         * {@link Hazelcast4IndexedSessionRepository} to work with Vaadin.
         *
         * @return the session-repository customizer
         */
        @Bean(BEAN_NAME)
        @ConditionalOnClass(Hazelcast4IndexedSessionRepository.class)
        public SessionRepositoryCustomizer<Hazelcast4IndexedSessionRepository> hazelcastSessionRepositoryCustomizer() {
            return repository -> {
                repository.setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
            };
        }

        @Bean
        @ConditionalOnMissingBean
        @SpringSessionHazelcastInstance
        public HazelcastInstance hazelcastInstance() {
            return super.hazelcastInstance();
        }

        @Override
        protected void configure(Config config) {
            configureMapAttributes(config);
            configureSessionSerializer(config);
        }

        private static void configureMapAttributes(Config config) {
            final var attrConfig = new AttributeConfig();
            attrConfig.setName(
                    Hazelcast4IndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE)
                    .setExtractorClassName(
                            Hazelcast4PrincipalNameExtractor.class.getName());

            final var indexConfig = new IndexConfig(IndexType.HASH,
                    Hazelcast4IndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE);

            config.getMapConfig(
                    Hazelcast4IndexedSessionRepository.DEFAULT_SESSION_MAP_NAME)
                    .addAttributeConfig(attrConfig).addIndexConfig(indexConfig);
        }

        private void configureSessionSerializer(Config config) {
            final var serializerConfig = new SerializerConfig();
            serializerConfig.setImplementation(getSerializer());
            serializerConfig.setTypeClass(MapSession.class);

            config.getSerializationConfig()
                    .addSerializerConfig(serializerConfig);
        }

        private Serializer getSerializer() {
            return new HazelcastSessionSerializer();
        }
    }

    @AutoConfiguration
    @ConditionalOnClass(HazelcastInstance.class)
    public static class HazelcastConfiguration extends HazelcastSupport {
        public HazelcastConfiguration(AzureKitProperties properties) {
            super(properties);
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
            return super.hazelcastInstance();
        }
    }

    static class HazelcastSupport {
        final AzureKitProperties properties;

        public HazelcastSupport(AzureKitProperties properties) {
            this.properties = properties;
        }

        HazelcastInstance hazelcastInstance() {
            final var config = new Config();

            configure(config);
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
            final var k8sProperties = properties.getHazelcast().getKubernetes();
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
