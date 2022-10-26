package com.vaadin.azure.starter;

import com.hazelcast.config.AttributeConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.IndexType;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.nio.serialization.Serializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.hazelcast.Hazelcast4IndexedSessionRepository;
import org.springframework.session.hazelcast.Hazelcast4PrincipalNameExtractor;
import org.springframework.session.hazelcast.HazelcastSessionSerializer;
import org.springframework.session.hazelcast.config.annotation.SpringSessionHazelcastInstance;
import org.springframework.util.StringUtils;

import com.vaadin.azure.starter.sessiontracker.SessionListener;
import com.vaadin.azure.starter.sessiontracker.SessionSerializer;
import com.vaadin.azure.starter.sessiontracker.SessionTrackerFilter;
import com.vaadin.azure.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.azure.starter.sessiontracker.backend.HazelcastConnector;
import com.vaadin.azure.starter.sessiontracker.backend.RedisConnector;
import com.vaadin.azure.starter.sessiontracker.push.PushSendListener;
import com.vaadin.azure.starter.sessiontracker.push.PushSessionTracker;

/**
 * This configuration bean is provided to auto-configure Vaadin apps to run in a
 * clustered environment.
 */
@AutoConfiguration
@EnableConfigurationProperties(AzureKitProperties.class)
public class AzureKitConfiguration {

    @AutoConfiguration
    public static class VaadinReplicatedSessionConfiguration {

        @Bean
        @Order(Integer.MIN_VALUE + 50)
        SessionTrackerFilter sessionTrackerFilter(
                SessionSerializer sessionSerializer) {
            return new SessionTrackerFilter(sessionSerializer);
        }

        @Bean
        SessionSerializer sessionSerializer(BackendConnector backendConnector) {
            return new SessionSerializer(backendConnector);
        }

        @Bean
        SessionListener sessionListener(BackendConnector backendConnector,
                SessionSerializer sessionSerializer) {
            return new SessionListener(backendConnector, sessionSerializer);
        }

        @Bean
        PushSendListener pushSendListener(SessionSerializer sessionSerializer) {
            return new PushSessionTracker(sessionSerializer);
        }

    }

    @AutoConfiguration
    // @EnableRedisHttpSession
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

        @Bean
        RedisConnector redisConnector() {
            return new RedisConnector();
        }

    }

    @AutoConfiguration
    // @EnableHazelcastHttpSession
    @ConditionalOnClass(Hazelcast4IndexedSessionRepository.class)
    public static class HazelcastSessionRepositoryConfiguration {

        static final String BEAN_NAME = "vaadinHazelcastSessionRepositoryCustomizer";

        private final AzureKitProperties properties;

        public HazelcastSessionRepositoryConfiguration(
                AzureKitProperties properties) {
            this.properties = properties;
        }

        @Bean
        HazelcastConnector hazelcastConnector(
                HazelcastInstance hazelcastInstance) {
            return new HazelcastConnector(hazelcastInstance);
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
            final var config = new Config();

            configureMapAttributes(config);
            configureSessionSerializer(config);
            configureKubernetes(config);

            return createHazelcastInstance(config);
        }

        // Package protected for testing purposes
        HazelcastInstance createHazelcastInstance(Config config) {
            return Hazelcast.newHazelcastInstance(config);
        }

        private void configureMapAttributes(Config config) {
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

        private Serializer getSerializer() {
            return new HazelcastSessionSerializer();
        }
    }
}
