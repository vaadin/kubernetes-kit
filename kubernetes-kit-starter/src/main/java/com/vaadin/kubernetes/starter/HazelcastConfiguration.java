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

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import com.vaadin.kubernetes.starter.sessiontracker.backend.HazelcastConnector;

/**
 * Auto-configuration for Hazelcast-based session backend.
 */
@AutoConfiguration
@ConditionalOnClass(HazelcastInstance.class)
@EnableConfigurationProperties(HazelcastProperties.class)
public class HazelcastConfiguration {

    final HazelcastProperties properties;

    public HazelcastConfiguration(HazelcastProperties properties) {
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
        // Make sure Hazelcast shutdown hook is disabled so that the
        // instance will be stopped after SessionSerializer saved the
        // latest pending state
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
        final var k8sServiceName = properties.getServiceName();

        if (StringUtils.hasText(k8sServiceName)) {
            final var networkConfig = config.getNetworkConfig().getJoin();
            networkConfig.getTcpIpConfig().setEnabled(false);
            networkConfig.getMulticastConfig().setEnabled(false);

            final var k8sConfig = networkConfig.getKubernetesConfig();
            k8sConfig.setEnabled(true);

            final var k8sNamespace = properties.getNamespace();
            k8sConfig.setProperty("namespace", k8sNamespace);
            k8sConfig.setProperty("service-name", k8sServiceName);
            k8sConfig.setProperty("service-port",
                    Integer.toString(properties.getServicePort()));
        }
    }

}
