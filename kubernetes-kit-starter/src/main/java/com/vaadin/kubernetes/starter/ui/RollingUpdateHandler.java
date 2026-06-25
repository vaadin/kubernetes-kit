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

import java.util.List;
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
 * Handles rolling updates for Vaadin applications running in a cluster.
 * <p>
 * When the ingress controller or gateway sets the
 * {@link KubernetesKitProperties#getUpdateVersionHeaderName() update version
 * header} on requests to the current (old) version, and its value differs from
 * the configured {@link KubernetesKitProperties#getAppVersion() application
 * version}, a notification is shown prompting the user to switch to the new
 * version. When the user accepts, the
 * {@link KubernetesKitProperties#getStickySessionCookieName() sticky session
 * cookie} is removed and the session is invalidated, so that the next request
 * is routed to a pod running the new version.
 *
 * @since 3.0
 */
public class RollingUpdateHandler implements VaadinServiceInitListener {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(RollingUpdateHandler.class);

    private final String appVersion;

    private final String updateVersionHeaderName;

    private final List<String> stickySessionCookieNames;

    private SwitchVersionListener switchVersionListener;

    /**
     * Creates a new {@code RollingUpdateHandler} instance.
     *
     * @param appVersion
     *            the application version. When the update version header value
     *            differs from this version, a notification is shown prompting
     *            the user to switch to the new version. If {@code null},
     *            rolling update version detection is disabled.
     * @param stickySessionCookieNames
     *            the names of the cookies used by the ingress controller or
     *            gateway implementation for sticky sessions. All cookies in the
     *            list are removed when the user accepts a version switch, so
     *            that the next request is no longer pinned to the old pod.
     *            Multiple names are needed when the gateway sets more than one
     *            affinity cookie (e.g. Azure Application Gateway with AGIC).
     * @param updateVersionHeaderName
     *            the name of the HTTP request header used to detect a new
     *            application version during rolling updates. The ingress
     *            controller or gateway sets this header on requests to the
     *            current version with the new version as its value.
     * @see KubernetesKitProperties#getAppVersion()
     * @see KubernetesKitProperties#getStickySessionCookieNames()
     * @see KubernetesKitProperties#getUpdateVersionHeaderName()
     */
    public RollingUpdateHandler(String appVersion,
            List<String> stickySessionCookieNames,
            String updateVersionHeaderName) {
        this.appVersion = appVersion;
        this.stickySessionCookieNames = stickySessionCookieNames != null
                ? stickySessionCookieNames
                : List.of();
        this.updateVersionHeaderName = updateVersionHeaderName;
    }

    /**
     * Creates a new {@code RollingUpdateHandler} instance with a single sticky
     * session cookie name.
     *
     * @param appVersion
     *            the application version. When the update version header value
     *            differs from this version, a notification is shown prompting
     *            the user to switch to the new version. If {@code null},
     *            rolling update version detection is disabled.
     * @param stickySessionCookieName
     *            the name of the cookie used by the ingress controller or
     *            gateway implementation for sticky sessions.
     * @param updateVersionHeaderName
     *            the name of the HTTP request header used to detect a new
     *            application version during rolling updates.
     * @see KubernetesKitProperties#getAppVersion()
     * @see KubernetesKitProperties#getStickySessionCookieNames()
     * @see KubernetesKitProperties#getUpdateVersionHeaderName()
     * @deprecated Use {@link #RollingUpdateHandler(String, List, String)}
     *             instead to support multiple sticky session cookie names.
     */
    @Deprecated(forRemoval = true)
    public RollingUpdateHandler(String appVersion,
            String stickySessionCookieName, String updateVersionHeaderName) {
        this(appVersion,
                stickySessionCookieName != null
                        ? List.of(stickySessionCookieName)
                        : List.of(),
                updateVersionHeaderName);
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
                    "Application version not configured. RollingUpdateHandler service not initialized.");
            return;
        }
        LOGGER.info(
                "RollingUpdateHandler service initialized. Registering RequestHandler with Application Version: {}",
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
        if (stickySessionCookieNames == null || stickySessionCookieNames.isEmpty()) {
            return;
        }
        VaadinResponse response = VaadinResponse.getCurrent();
        LOGGER.debug("Removing cookies: {}.", stickySessionCookieNames);
        for (String name : stickySessionCookieNames) {
            Cookie cookie = new Cookie(name, "");
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        }
    }

}
