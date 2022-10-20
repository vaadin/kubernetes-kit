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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.session.FindByIndexNameSessionRepository;
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

import com.vaadin.azure.starter.push.CloudPushConnectionListener;
import com.vaadin.azure.starter.push.SpringSessionCloudPushConnectionListener;

/**
 * This configuration bean is provided to auto-configure Vaadin apps to run in a
 * clustered environment.
 */
@AutoConfiguration
@EnableConfigurationProperties(AzureKitProperties.class)
public class AzureKitConfiguration {

    @Bean
    @Primary
    @ConditionalOnBean(FindByIndexNameSessionRepository.class)
    DelegateSessionRepository primarySessionRepository(
            FindByIndexNameSessionRepository delegate) {
        return new DelegateSessionRepository(delegate);
    }

    @Bean
    CloudPushConnectionListener pushConnectionListener(DelegateSessionRepository repository) {
        return new SpringSessionCloudPushConnectionListener(repository);
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
    public static class HazelcastSessionRepositoryConfiguration {

        static final String BEAN_NAME = "vaadinHazelcastSessionRepositoryCustomizer";

        private final AzureKitProperties properties;

        public HazelcastSessionRepositoryConfiguration(
                AzureKitProperties properties) {
            this.properties = properties;
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
            }
        }

        private Serializer getSerializer() {
            return new HazelcastSessionSerializer();
        }
    }
}
