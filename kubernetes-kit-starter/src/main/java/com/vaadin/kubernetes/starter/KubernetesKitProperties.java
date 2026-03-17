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
     * Enables or disables rolling update support, which handles version update
     * notifications and sticky session cookie removal during rolling updates.
     *
     * @see com.vaadin.kubernetes.starter.ui.RollingUpdateHandler
     */
    private boolean rollingUpdates = true;

    /**
     * The name of the HTTP request header used to detect a new application
     * version during rolling updates.
     * <p>
     * When the ingress controller or gateway sets this header on requests to
     * the current (old) version, and its value differs from the application's
     * own version (set via the {@code APP_VERSION} environment variable),
     * {@link com.vaadin.kubernetes.starter.ui.RollingUpdateHandler RollingUpdateHandler}
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
     * The application version used during rolling updates.
     * <p>
     * When the ingress controller or gateway sets the
     * {@link #updateVersionHeaderName update version header} on requests to the
     * current (old) version, its value is compared against this property to
     * determine whether a version update notification should be shown.
     * <p>
     * Defaults to the value of the {@code APP_VERSION} environment variable. If
     * this property is not set and the environment variable is not defined,
     * rolling update version detection is disabled.
     */
    private String appVersion = System.getenv("APP_VERSION");

    /**
     * The name of the cookie used by the ingress controller or gateway
     * implementation for sticky sessions (session affinity).
     * <p>
     * This must match the cookie name used by the infrastructure routing
     * traffic to the application. The cookie is removed by
     * {@link com.vaadin.kubernetes.starter.ui.RollingUpdateHandler RollingUpdateHandler}
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
     * Checks if auto-configuration of Kubernetes Kit is
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
     * Checks if rolling update support is enabled.
     *
     * @return {@code true} if rolling update support is enabled
     * @see com.vaadin.kubernetes.starter.ui.RollingUpdateHandler
     */
    public boolean isRollingUpdates() {
        return rollingUpdates;
    }

    /**
     * Enables or disables rolling update support.
     *
     * @param rollingUpdates
     *            {@code true} to enable, {@code false} to disable
     * @see com.vaadin.kubernetes.starter.ui.RollingUpdateHandler
     */
    public void setRollingUpdates(boolean rollingUpdates) {
        this.rollingUpdates = rollingUpdates;
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
     * Gets the application version used during rolling updates.
     *
     * @return the application version, or {@code null} if not set
     * @see #appVersion
     */
    public String getAppVersion() {
        return appVersion;
    }

    /**
     * Sets the application version used during rolling updates.
     *
     * @param appVersion
     *            the application version
     * @see #appVersion
     */
    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
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

}
