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

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.vaadin.kubernetes.starter.sessiontracker.SameSite;

/**
 * Definition of configuration properties for the Kubernetes Kit starter.
 *
 * @author Vaadin Ltd
 * @since 1.0
 */
@ConfigurationProperties(prefix = KubernetesKitProperties.PREFIX)
public class KubernetesKitProperties {

    /**
     * The prefix for Kubernetes Kit starter properties.
     */
    public static final String PREFIX = "vaadin.kubernetes";

    /**
     * Enables (or disables) auto-configuration.
     */
    private boolean autoConfigure = true;

    /**
     * Value of the distributed storage session key cookie's SameSite attribute.
     */
    private SameSite clusterKeyCookieSameSite = SameSite.STRICT;

    /**
     * Hazelcast configuration properties.
     */
    private HazelcastProperties hazelcast = new HazelcastProperties();

    /**
     * Checks if auto-configuration of {@link KubernetesKitConfiguration} is
     * enabled.
     *
     * @return true, if auto-configuration is enabled
     */
    public boolean isAutoConfigure() {
        return autoConfigure;
    }

    /**
     * Enables or disables auto-configuration of
     * {@link KubernetesKitConfiguration}.
     *
     * @param autoConfigure
     *            {@code true} to enable auto-configuration, {@code false} to
     *            disable
     */
    public void setAutoConfigure(boolean autoConfigure) {
        this.autoConfigure = autoConfigure;
    }

    /**
     * Gets the distributed storage session key cookie's SameSite attribute
     * value.
     *
     * @return the distributed storage session key cookie's SameSite attribute
     *         value
     */
    public SameSite getClusterKeyCookieSameSite() {
        return clusterKeyCookieSameSite;
    }

    /**
     * Sets the distributed storage session key cookie's SameSite attribute.
     *
     * @param sameSite
     *            value of the distributed storage session key cookie's SameSite
     *            attribute
     */
    public void setClusterKeyCookieSameSite(SameSite sameSite) {
        this.clusterKeyCookieSameSite = sameSite;
    }

    /**
     * Gets Hazelcast configuration properties.
     *
     * @return the Hazelcast configuration properties
     */
    public HazelcastProperties getHazelcast() {
        return hazelcast;
    }

    /**
     * Sets Hazelcast configuration properties.
     *
     * @param hazelcast
     *            the Hazelcast configuration properties
     */
    public void setHazelcast(HazelcastProperties hazelcast) {
        this.hazelcast = hazelcast;
    }

    /**
     * Hazelcast configuration properties.
     */
    public static class HazelcastProperties {

        private String namespace = "default";

        private String serviceName;

        private int servicePort = 0;

        /**
         * Gets the Kubernetes namespace to use.
         *
         * @return the namespace
         */
        public String getNamespace() {
            return namespace;
        }

        /**
         * Sets the Kubernetes namespace to use. If not set, the default value
         * is {@code default}.
         *
         * @param namespace
         *            the namespace
         */
        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        /**
         * Gets the service name of the Kubernetes service exposing the
         * Hazelcast port to the cluster.
         *
         * @return the service name
         */
        public String getServiceName() {
            return serviceName;
        }

        /**
         * Sets the service name of the Kubernetes service exposing the
         * Hazelcast port to the cluster.
         *
         * @param serviceName
         *            the service name
         */
        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        /**
         * Gets endpoint port of the Hazelcast service
         *
         * If specified with a value greater than 0, it overrides the default; 0
         * by default.
         *
         * @return endpoint port or 0
         */
        public int getServicePort() {
            return servicePort;
        }

        /**
         * Sets endpoint port of the Hazelcast service.
         *
         * Uxd
         *
         * @param port
         *            port number or 0
         */
        public void setServicePort(int port) {
            this.servicePort = port;
        }
    }

}
