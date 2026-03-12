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

import jakarta.servlet.http.Cookie;

import java.io.Serial;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.kubernetes.starter.KubernetesKitProperties;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;

/**
 * Cluster support for Vaadin applications. This component allows receiving
 * events from the cluster running the Vaadin application.
 */
public class ClusterSupport implements VaadinServiceInitListener {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(ClusterSupport.class);

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

    private final String appVersion;

    private final String updateVersionHeaderName;

    private final String stickySessionCookieName;

    private SwitchVersionListener switchVersionListener;

    /**
     * Creates a new {@code ClusterSupport} instance with default values.
     *
     * @deprecated Use
     *             {@link #ClusterSupport(String, String, String)} to provide
     *             configurable application version, cookie and header names.
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("deprecation")
    public ClusterSupport() {
        this(System.getenv(ENV_APP_VERSION), STICKY_CLUSTER_COOKIE,
                UPDATE_VERSION_HEADER);
    }

    /**
     * Creates a new {@code ClusterSupport} instance.
     *
     * @param stickySessionCookieName
     *            the name of the cookie used by the ingress controller or
     *            gateway implementation for sticky sessions. This must match
     *            the cookie name configured in the infrastructure routing
     *            traffic to the application. When the user accepts a version
     *            switch, this cookie is removed so that the next request is no
     *            longer pinned to the old pod.
     * @param updateVersionHeaderName
     *            the name of the HTTP request header used to detect a new
     *            application version during rolling updates. The ingress
     *            controller or gateway sets this header on requests to the
     *            current version with the new version as its value.
     * @deprecated Use
     *             {@link #ClusterSupport(String, String, String)} to provide
     *             the application version.
     * @see KubernetesKitProperties#getStickySessionCookieName()
     * @see KubernetesKitProperties#getUpdateVersionHeaderName()
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("deprecation")
    public ClusterSupport(String stickySessionCookieName,
            String updateVersionHeaderName) {
        this(System.getenv(ENV_APP_VERSION), stickySessionCookieName,
                updateVersionHeaderName);
    }

    /**
     * Creates a new {@code ClusterSupport} instance.
     *
     * @param appVersion
     *            the application version. When the update version header value
     *            differs from this version, a notification is shown prompting
     *            the user to switch to the new version. If {@code null},
     *            rolling update version detection is disabled.
     * @param stickySessionCookieName
     *            the name of the cookie used by the ingress controller or
     *            gateway implementation for sticky sessions. This must match
     *            the cookie name configured in the infrastructure routing
     *            traffic to the application. When the user accepts a version
     *            switch, this cookie is removed so that the next request is no
     *            longer pinned to the old pod.
     * @param updateVersionHeaderName
     *            the name of the HTTP request header used to detect a new
     *            application version during rolling updates. The ingress
     *            controller or gateway sets this header on requests to the
     *            current version with the new version as its value.
     * @see KubernetesKitProperties#getAppVersion()
     * @see KubernetesKitProperties#getStickySessionCookieName()
     * @see KubernetesKitProperties#getUpdateVersionHeaderName()
     */
    public ClusterSupport(String appVersion, String stickySessionCookieName,
            String updateVersionHeaderName) {
        this.appVersion = appVersion;
        this.stickySessionCookieName = stickySessionCookieName;
        this.updateVersionHeaderName = updateVersionHeaderName;
    }

    /**
     * Register the global version switch listener. If set to <code>null</code>
     * the current session and the sticky cluster cookie are removed without any
     * version switch condition check.
     *
     * @param listener
     *            the listener to register.
     */
    public void setSwitchVersionListener(SwitchVersionListener listener) {
        this.switchVersionListener = listener;
    }

    @Override
    public void serviceInit(ServiceInitEvent serviceInitEvent) {
        if (appVersion == null) {
            LOGGER.debug(
                    "Application version not configured. ClusterSupport service not initialized.");
            return;
        }
        LOGGER.info(
                "ClusterSupport service initialized. Registering RequestHandler with Application Version: {}",
                appVersion);

        // Register a generic request handler for all the requests
        serviceInitEvent.addRequestHandler(this::handleRequest);
    }

    private boolean handleRequest(VaadinSession vaadinSession,
            VaadinRequest vaadinRequest, VaadinResponse vaadinResponse) {
        String versionHeader = vaadinRequest.getHeader(updateVersionHeaderName);

        vaadinSession.access(() -> {

            // Always check for the update version header
            WrappedSession session = vaadinSession.getSession();
            vaadinSession.getUIs().forEach(ui -> {
                Optional<Component> versionNotifier = ui.getChildren()
                        .filter(child -> (child instanceof VersionNotifier))
                        .findFirst();
                if (versionNotifier.isPresent()) {
                    // Remove the notifier in case of version roll-back or
                    // when the proxy is not setting the update version header
                    if (versionHeader == null || versionHeader.isEmpty()
                            || appVersion.equals(versionHeader)) {
                        LOGGER.info(
                                "Removing notifier: updateVersion={}, appVersion={}, session={}",
                                versionHeader, appVersion, session.getId());
                        ui.remove(versionNotifier.get());
                    }
                } else if (versionHeader != null && !versionHeader.isEmpty()
                        && !appVersion.equals(versionHeader)) {
                    // Show notifier because versions do not match
                    VersionNotifier notifier = new VersionNotifier(appVersion,
                            versionHeader);
                    notifier.addSwitchVersionEventListener(
                            this::onComponentEvent);
                    LOGGER.info(
                            "Notifying version update: updateVersion={}, appVersion={}, session={}",
                            versionHeader, appVersion, session.getId());
                    ui.add(notifier);
                }
            });
        });

        // If the current and the new versions do not match notify the user
        return false;
    }

    private void onComponentEvent(VersionNotifier.SwitchVersionEvent event) {
        if (switchVersionListener != null) {
            // Do nothing if switch version listener prevents switching
            if (!switchVersionListener.nodeSwitch(VaadinRequest.getCurrent(),
                    VaadinResponse.getCurrent())) {
                LOGGER.debug(
                        "Application version switch prevented by switch listener.");
                return;
            }

            // Do application level clean-up before version switch
            switchVersionListener.doAppCleanup();
        }

        // When the user clicks on the notifier remove the sticky cluster
        // cookie and invalidate the session
        removeStickyClusterCookie();
        WrappedSession session = VaadinRequest.getCurrent().getWrappedSession();
        LOGGER.debug("Invalidating session {}", session.getId());
        session.invalidate();
    }

    private void removeStickyClusterCookie() {
        LOGGER.debug("Removing cookie '{}'.", stickySessionCookieName);
        Cookie cookie = new Cookie(stickySessionCookieName, "");
        cookie.setMaxAge(0);
        VaadinResponse.getCurrent().addCookie(cookie);
    }

}
