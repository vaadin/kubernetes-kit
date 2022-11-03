package com.vaadin.azure.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Definition of configuration properties for the Azure Kit starter.
 *
 * @author Vaadin Ltd
 * @since 1.0
 */
@ConfigurationProperties(prefix = AzureKitProperties.PREFIX)
public class AzureKitProperties {

    /**
     * The prefix for Azure Kit starter properties.
     */
    public static final String PREFIX = "vaadin.azure";

    /**
     * Hazelcast configuration properties.
     */
    private HazelcastProperties hazelcast = new HazelcastProperties();

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

        private HazelcastKubernetesProperties kubernetes = new HazelcastKubernetesProperties();

        /**
         * Gets Hazelcast properties to run in Kubernetes clusters.
         *
         * @return the properties
         */
        public HazelcastKubernetesProperties getKubernetes() {
            return kubernetes;
        }

        /**
         * Sets Hazelcast properties to run in Kubernetes clusters.
         *
         * @param kubernetes
         *            the properties
         */
        public void setKubernetes(HazelcastKubernetesProperties kubernetes) {
            this.kubernetes = kubernetes;
        }
    }

    /**
     * Hazelcast Kubernetes properties.
     */
    public static class HazelcastKubernetesProperties {

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
         * @param port port number or 0
         */
        public void setServicePort(int port) {
            this.servicePort = port;
        }
    }
}
