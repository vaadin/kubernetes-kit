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
import org.springframework.context.annotation.Bean;
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

/**
 * This configuration bean is provided to auto-configure Vaadin apps to run in a
 * clustered environment.
 */
@AutoConfiguration
public class AzureKitConfiguration {

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

            final var serializerConfig = new SerializerConfig();
            serializerConfig.setImplementation(getSerializer());
            serializerConfig.setTypeClass(MapSession.class);

            config.getSerializationConfig()
                    .addSerializerConfig(serializerConfig);

            config.getNetworkConfig().getJoin().getTcpIpConfig()
                    .setEnabled(false);
            config.getNetworkConfig().getJoin().getMulticastConfig()
                    .setEnabled(false);
            config.getNetworkConfig().getJoin().getKubernetesConfig()
                    .setEnabled(true).setProperty("namespace", "default")
                    .setProperty("service-name", "azure-kit-hazelcast-service");

            return Hazelcast.newHazelcastInstance(config);
        }

        private Serializer getSerializer() {
            return new HazelcastSessionSerializer();
        }
    }
}
