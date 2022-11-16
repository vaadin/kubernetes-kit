package com.vaadin.kubernetes.starter;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KubernetesKitConfigurationTest {

    @Test
    public void hazelcastInstance_serviceNameSet_kubernetesConfigured() {
        var prop = new KubernetesKitProperties();
        prop.getHazelcast().setNamespace("foo-namespace");
        prop.getHazelcast().setServiceName("foo-service");

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

    private final class MockHazelcastConfiguration
            extends KubernetesKitConfiguration.HazelcastConfiguration {

        public MockHazelcastConfiguration(KubernetesKitProperties properties) {
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
