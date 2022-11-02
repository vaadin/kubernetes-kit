package com.vaadin.azure.starter;

import com.hazelcast.config.AttributeConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.IndexType;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.Test;
import org.springframework.session.SaveMode;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.hazelcast.Hazelcast4IndexedSessionRepository;
import org.springframework.session.hazelcast.Hazelcast4PrincipalNameExtractor;
import org.springframework.session.hazelcast.HazelcastSessionSerializer;

import com.vaadin.azure.starter.AzureKitConfiguration.HazelcastSessionRepositoryConfiguration;
import com.vaadin.azure.starter.AzureKitConfiguration.RedisSessionRepositoryConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AzureKitConfigurationTest {

    @Test
    public void redisRepositoryAvailable_saveModeConfigured() {
        var configuration = new RedisSessionRepositoryConfiguration();
        var customizer = configuration.redisSessionRepositoryCustomizer();
        var mockRepository = mock(RedisIndexedSessionRepository.class);

        customizer.customize(mockRepository);

        verify(mockRepository).setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
    }

    @Test
    public void hazelcastRepositoryAvailable_saveModeConfigured() {
        var configuration = new HazelcastSessionRepositoryConfiguration(new AzureKitProperties());
        var customizer = configuration.hazelcastSessionRepositoryCustomizer();
        var mockRepository = mock(Hazelcast4IndexedSessionRepository.class);

        customizer.customize(mockRepository);

        verify(mockRepository).setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
    }

    @Test
    public void hazelcastInstance_principalAttributeConfigured() {
        var prop = new AzureKitProperties();
        var configuration = new MockHazelcastConfiguration(prop);

        var hz = configuration.hazelcastInstance();
        var mapConfig = hz.getConfig().getMapConfig(
                Hazelcast4IndexedSessionRepository.DEFAULT_SESSION_MAP_NAME);
        var hasPrincipalAttribute = mapConfig.getAttributeConfigs().stream()
                .anyMatch(this::matchingPrincipalAttribute);

        assertTrue(hasPrincipalAttribute);
    }

    @Test
    public void hazelcastInstance_principalHashIndexConfigured() {
        var prop = new AzureKitProperties();
        var configuration = new MockHazelcastConfiguration(prop);

        var hz = configuration.hazelcastInstance();
        var mapConfig = hz.getConfig().getMapConfig(
                Hazelcast4IndexedSessionRepository.DEFAULT_SESSION_MAP_NAME);
        var hasPrincipalHashIndex = mapConfig.getIndexConfigs().stream()
                .anyMatch(this::matchingPrincipalHashIndex);

        assertTrue(hasPrincipalHashIndex);
    }

    @Test
    public void hazelcastInstance_sessionSerializerSet() {
        var prop = new AzureKitProperties();
        var configuration = new MockHazelcastConfiguration(prop);

        var hz = configuration.hazelcastInstance();
        var hasHazelcastSessionSerializer = hz.getConfig()
                .getSerializationConfig().getSerializerConfigs().stream()
                .anyMatch(this::matchingHazelcastSessionSerializer);

        assertTrue(hasHazelcastSessionSerializer);
    }

    @Test
    public void hazelcastInstance_serviceNameSet_kubernetesConfigured() {
        var prop = new AzureKitProperties();
        prop.getHazelcast().getKubernetes().setNamespace("foo-namespace");
        prop.getHazelcast().getKubernetes().setServiceName("foo-service");

        var configuration = new MockHazelcastConfiguration(prop);

        var hz = configuration.hazelcastInstance();
        var networkConfig = hz.getConfig().getNetworkConfig().getJoin();

        assertFalse(networkConfig.getTcpIpConfig().isEnabled());
        assertFalse(networkConfig.getMulticastConfig().isEnabled());

        var k8sConfig = networkConfig.getKubernetesConfig();
        var namespace = k8sConfig.getProperties().get("namespace");
        var serviceName = k8sConfig.getProperties().get("service-name");

        assertTrue(k8sConfig.isEnabled());
        assertEquals("foo-namespace", namespace);
        assertEquals("foo-service", serviceName);
    }

    private boolean matchingHazelcastSessionSerializer(
            SerializerConfig config) {
        return config.getImplementation().getClass()
                .equals(HazelcastSessionSerializer.class);
    }

    private boolean matchingPrincipalHashIndex(IndexConfig config) {
        return config.getType().equals(IndexType.HASH)
                && config.getAttributes().contains(
                        Hazelcast4IndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE);
    }

    private boolean matchingPrincipalAttribute(AttributeConfig config) {
        return config.getName().equals(
                Hazelcast4IndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE)
                && config.getExtractorClassName().equals(
                        Hazelcast4PrincipalNameExtractor.class.getName());
    }

    private final class MockHazelcastConfiguration
            extends AzureKitConfiguration.HazelcastSessionRepositoryConfiguration {

        public MockHazelcastConfiguration(AzureKitProperties properties) {
            super(properties);
        }

        @Override
        HazelcastInstance createHazelcastInstance(Config config) {
            final var mockHazelcastInstance = mock(HazelcastInstance.class);
            when(mockHazelcastInstance.getConfig()).thenReturn(config);
            return mockHazelcastInstance;
        }
    }
}
