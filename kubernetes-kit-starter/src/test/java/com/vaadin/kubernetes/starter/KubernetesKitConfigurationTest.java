package com.vaadin.kubernetes.starter;

import java.io.IOException;
import java.util.Properties;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import com.vaadin.pro.licensechecker.LicenseChecker;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class KubernetesKitConfigurationTest {

    static final String PROPERTIES_RESOURCE = "kubernetes-kit.properties";

    @Test
    public void correctVersionPassedToLicenseChecker() {
        final var version = getProperties().getProperty("version");

        assertThat(version,
                startsWith(KubernetesKitConfiguration.PRODUCT_VERSION));
    }

    @Test
    public void licenseChecker_licenseIsCheckedFromStaticBlock() {
        final var mockController = mockStatic(LicenseChecker.class);

        new KubernetesKitConfiguration();

        mockController.verify(() -> LicenseChecker.checkLicenseFromStaticBlock(
                KubernetesKitConfiguration.PRODUCT_NAME,
                KubernetesKitConfiguration.PRODUCT_VERSION, null));

        mockController.close();
    }

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

    private Properties getProperties() {
        try {
            return PropertiesLoaderUtils.loadAllProperties(PROPERTIES_RESOURCE);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
