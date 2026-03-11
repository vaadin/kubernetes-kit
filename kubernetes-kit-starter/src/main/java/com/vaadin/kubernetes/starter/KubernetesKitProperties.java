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

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.vaadin.kubernetes.starter.sessiontracker.CurrentKey;
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
     * Amount of time to be added to the HTTP session timeout to determine the
     * expiration of the backend session.
     */
    private Duration backendSessionExpirationTolerance;

    /**
     * The name of the distributed storage session key cookie.
     */
    private String clusterKeyCookieName = CurrentKey.COOKIE_NAME;

    /**
     * Value of the distributed storage session key cookie's SameSite attribute.
     */
    private SameSite clusterKeyCookieSameSite = SameSite.STRICT;

    /**
     * Enables or disables
     * {@link com.vaadin.kubernetes.starter.ui.ClusterSupport ClusterSupport},
     * which handles version update notifications and sticky session cookie
     * removal during rolling updates.
     */
    private boolean clusterSupport = true;

    /**
     * The name of the HTTP request header used to detect a new application
     * version during rolling updates.
     * <p>
     * When the ingress controller or gateway sets this header on requests to
     * the current (old) version, and its value differs from the application's
     * own version (set via the {@code APP_VERSION} environment variable),
     * {@link com.vaadin.kubernetes.starter.ui.ClusterSupport ClusterSupport}
     * shows a notification prompting the user to switch to the new version.
     * <p>
     * With the Kubernetes Ingress API and NGINX Ingress, this header was
     * typically set via the {@code configuration-snippet} annotation
     * ({@code proxy_set_header}), which is deprecated since NGINX Ingress
     * 1.9.0.
     * <p>
     * With the Kubernetes Gateway API, use a {@code RequestHeaderModifier}
     * filter in the {@code HTTPRoute} resource to add this header with the new
     * version as its value.
     */
    private String updateVersionHeaderName = "X-AppUpdate";

    /**
     * The name of the cookie used by the ingress controller or gateway
     * implementation for sticky sessions (session affinity).
     * <p>
     * This must match the cookie name used by the infrastructure routing
     * traffic to the application. The cookie is removed by
     * {@link com.vaadin.kubernetes.starter.ui.ClusterSupport ClusterSupport}
     * when the user accepts a version switch, so that the next request is no
     * longer pinned to the old pod.
     * <p>
     * For the Kubernetes Ingress API with NGINX Ingress, the default cookie
     * name {@code INGRESSCOOKIE} is used when session affinity is enabled via
     * annotations.
     * <p>
     * For the Kubernetes Gateway API, the cookie name depends on the gateway
     * implementation and on the {@code sessionName} field in the
     * {@code sessionPersistence} configuration of the {@code HTTPRoute}
     * resource. If {@code sessionName} is set, use the same value here. If
     * {@code sessionName} is omitted, the gateway implementation generates an
     * implementation-specific cookie name that must be determined from the
     * implementation's documentation or by inspecting HTTP responses.
     */
    private String stickySessionCookieName = "INGRESSCOOKIE";

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
     * Sets the amount of time to be added to the HTTP session timeout to
     * determine the expiration of the backend session.
     */
    public void setBackendSessionExpirationTolerance(
            Duration backendSessionExpirationTolerance) {
        this.backendSessionExpirationTolerance = backendSessionExpirationTolerance;
    }

    /**
     * Gets the amount of time to be added to the HTTP session timeout to
     * determine the expiration of the backend session.
     */
    public Duration getBackendSessionExpirationTolerance() {
        return backendSessionExpirationTolerance;
    }

    /**
     * Gets the name of the distributed storage session key cookie.
     *
     * @return the name of the distributed storage session key cookie
     */
    public String getClusterKeyCookieName() {
        return clusterKeyCookieName;
    }

    /**
     * Sets the name of the distributed storage session key cookie.
     *
     * @param clusterKeyCookieName
     *            the name of the distributed storage session key cookie
     */
    public void setClusterKeyCookieName(String clusterKeyCookieName) {
        this.clusterKeyCookieName = clusterKeyCookieName;
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
     * Checks if
     * {@link com.vaadin.kubernetes.starter.ui.ClusterSupport ClusterSupport}
     * is enabled.
     *
     * @return {@code true} if cluster support is enabled
     */
    public boolean isClusterSupport() {
        return clusterSupport;
    }

    /**
     * Enables or disables
     * {@link com.vaadin.kubernetes.starter.ui.ClusterSupport ClusterSupport}.
     *
     * @param clusterSupport
     *            {@code true} to enable, {@code false} to disable
     */
    public void setClusterSupport(boolean clusterSupport) {
        this.clusterSupport = clusterSupport;
    }

    /**
     * Gets the name of the HTTP request header used to detect a new application
     * version during rolling updates.
     *
     * @return the update version header name
     * @see #updateVersionHeaderName
     */
    public String getUpdateVersionHeaderName() {
        return updateVersionHeaderName;
    }

    /**
     * Sets the name of the HTTP request header used to detect a new application
     * version during rolling updates.
     *
     * @param updateVersionHeaderName
     *            the update version header name
     * @see #updateVersionHeaderName
     */
    public void setUpdateVersionHeaderName(String updateVersionHeaderName) {
        this.updateVersionHeaderName = updateVersionHeaderName;
    }

    /**
     * Gets the name of the cookie used by the ingress controller or gateway
     * implementation for sticky sessions.
     *
     * @return the sticky session cookie name
     * @see #stickySessionCookieName
     */
    public String getStickySessionCookieName() {
        return stickySessionCookieName;
    }

    /**
     * Sets the name of the cookie used by the ingress controller or gateway
     * implementation for sticky sessions.
     *
     * @param stickySessionCookieName
     *            the sticky session cookie name
     * @see #stickySessionCookieName
     */
    public void setStickySessionCookieName(String stickySessionCookieName) {
        this.stickySessionCookieName = stickySessionCookieName;
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
