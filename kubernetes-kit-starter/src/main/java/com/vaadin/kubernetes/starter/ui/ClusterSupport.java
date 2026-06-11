/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.ui;

import com.vaadin.kubernetes.starter.KubernetesKitProperties;

/**
 * Cluster support for Vaadin applications.
 *
 * @deprecated Use {@link RollingUpdateHandler} instead.
 */
@Deprecated(forRemoval = true)
public class ClusterSupport extends RollingUpdateHandler {

    /**
     * Version environment variable name.
     *
     * @deprecated Use {@link KubernetesKitProperties#getAppVersion()} to
     *             configure the application version instead.
     */
    @Deprecated(forRemoval = true)
    public static final String ENV_APP_VERSION = "APP_VERSION";

    /**
     * Update version header name.
     *
     * @deprecated Use
     *             {@link KubernetesKitProperties#getUpdateVersionHeaderName()}
     *             to configure the update version header name instead.
     */
    @Deprecated(forRemoval = true)
    public static final String UPDATE_VERSION_HEADER = "X-AppUpdate";

    /**
     * Sticky cluster cookie name.
     *
     * @deprecated Use
     *             {@link KubernetesKitProperties#getStickySessionCookieName()}
     *             to configure the sticky session cookie name instead.
     */
    @Deprecated(forRemoval = true)
    public static final String STICKY_CLUSTER_COOKIE = "INGRESSCOOKIE";

    /**
     * Creates a new {@code ClusterSupport} instance with default values.
     *
     * @deprecated Use {@link RollingUpdateHandler} instead.
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("deprecation")
    public ClusterSupport() {
        super(System.getenv(ENV_APP_VERSION), STICKY_CLUSTER_COOKIE,
                UPDATE_VERSION_HEADER);
    }

    /**
     * Creates a new {@code ClusterSupport} instance.
     *
     * @param stickySessionCookieName
     *            the name of the sticky session cookie
     * @param updateVersionHeaderName
     *            the name of the update version header
     * @deprecated Use {@link RollingUpdateHandler} instead.
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("deprecation")
    public ClusterSupport(String stickySessionCookieName,
            String updateVersionHeaderName) {
        super(System.getenv(ENV_APP_VERSION), stickySessionCookieName,
                updateVersionHeaderName);
    }

    /**
     * Creates a new {@code ClusterSupport} instance.
     *
     * @param appVersion
     *            the application version
     * @param stickySessionCookieName
     *            the name of the sticky session cookie
     * @param updateVersionHeaderName
     *            the name of the update version header
     * @deprecated Use {@link RollingUpdateHandler} instead.
     */
    @Deprecated(forRemoval = true)
    public ClusterSupport(String appVersion, String stickySessionCookieName,
            String updateVersionHeaderName) {
        super(appVersion, stickySessionCookieName, updateVersionHeaderName);
    }

}
