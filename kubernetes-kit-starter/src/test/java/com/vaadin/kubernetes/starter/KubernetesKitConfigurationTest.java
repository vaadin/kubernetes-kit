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

import java.time.Duration;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionExpirationPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KubernetesKitConfigurationTest {

    @Test
    public void sessionExpirationPolicy_configNotSet_policyIsNever() {
        var prop = new KubernetesKitProperties();
        var cfg = new KubernetesKitConfiguration.VaadinReplicatedSessionConfiguration(
                prop);
        var sessionExpirationPolicy = cfg.sessionExpirationPolicy();
        Assertions.assertSame(sessionExpirationPolicy,
                SessionExpirationPolicy.NEVER);
    }

    @Test
    public void sessionExpirationPolicy_configSet_policyIsSessionTimeoutPlusTolerance() {
        var prop = new KubernetesKitProperties();
        prop.setBackendSessionExpirationTolerance(Duration.ofMinutes(10));
        var cfg = new KubernetesKitConfiguration.VaadinReplicatedSessionConfiguration(
                prop);
        var sessionExpirationPolicy = cfg.sessionExpirationPolicy();
        assertEquals(Duration.ofMinutes(40),
                sessionExpirationPolicy.apply(1800));

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
}
