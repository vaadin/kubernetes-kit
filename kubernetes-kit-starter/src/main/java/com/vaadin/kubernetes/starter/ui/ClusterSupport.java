package com.vaadin.kubernetes.starter.ui;

import javax.servlet.http.Cookie;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;

/**
 * Cluster support for Vaadin applications. This component allows receiving
 * events from the cluster running the Vaadin application.
 */
public class ClusterSupport implements VaadinServiceInitListener {
    private static final long serialVersionUID = 1L;

    /**
     * Version environment variable name.
     */
    public static final String ENV_APP_VERSION = "APP_VERSION";

    /**
     * Current version cookie name.
     */
    public static final String CURRENT_VERSION_COOKIE = "app-version";

    /**
     * Update version cookie name.
     */
    public static final String UPDATE_VERSION_COOKIE = "app-update";

    /**
     * Sticky cluster cookie name.
     */
    public static final String STICKY_CLUSTER_COOKIE = "INGRESSCOOKIE";

    private SwitchVersionListener switchVersionListener;

    private String appVersion;

    /**
     * Get the current instance of the ClusterSupport.
     */
    public static ClusterSupport getCurrent() {
        return CurrentInstance.get(ClusterSupport.class);
    }

    /**
     * Register the global version switch listener. If set to <code>null</code>
     * the current session and the cookies are removed without any version
     * switch condition check.
     *
     * @param listener
     *            the listener to register.
     */
    public void setSwitchVersionListener(SwitchVersionListener listener) {
        this.switchVersionListener = listener;
    }

    @Override
    public void serviceInit(ServiceInitEvent serviceInitEvent) {
        appVersion = System.getenv(ENV_APP_VERSION);
        if (appVersion == null) {
            getLogger().error(
                    "Missing environment variable 'APP_VERSION'. ClusterSupport service not initialized.");
            return;
        }
        getLogger().info(
                "ClusterSupport service initialized. Registering RequestHandler with Application Version: "
                        + appVersion);

        // Register a generic request handler for all the requests
        serviceInitEvent.addRequestHandler(this::handleRequest);
    }

    private boolean handleRequest(VaadinSession vaadinSession,
            VaadinRequest vaadinRequest, VaadinResponse vaadinResponse) {
        // Set the thread local instance
        CurrentInstance.set(ClusterSupport.class, this);

        vaadinSession.access(() -> {
            // Always set the version cookie
            Cookie currentVersionCookie = getCookieByName(
                    CURRENT_VERSION_COOKIE);
            if (currentVersionCookie == null
                    || !currentVersionCookie.getValue().equals(appVersion)) {
                currentVersionCookie = new Cookie(CURRENT_VERSION_COOKIE,
                        appVersion);
                currentVersionCookie.setHttpOnly(true);
                vaadinResponse.addCookie(currentVersionCookie);
            }

            // Always check for the new version cookie
            Cookie updateVersionCookie = getCookieByName(UPDATE_VERSION_COOKIE);
            if (updateVersionCookie != null
                    && !updateVersionCookie.getValue().isEmpty()
                    && !currentVersionCookie.getValue()
                            .equals(updateVersionCookie.getValue())) {
                vaadinSession.getUIs().forEach(ui -> {
                    if (ui.getChildren().anyMatch(
                            child -> (child instanceof VersionNotificator))) {
                        return;
                    }
                    VersionNotificator notificator = new VersionNotificator(
                            appVersion, updateVersionCookie.getValue());
                    notificator.addSwitchVersionEventListener(
                            this::onComponentEvent);
                    // Show notificator
                    ui.add(notificator);
                });
            }
        });

        // If the current and the new versions do not match notify the user
        return false;
    }

    private void onComponentEvent(VersionNotificator.SwitchVersionEvent event) {
        if (switchVersionListener != null) {
            // Do nothing if switch version listener prevents switching
            if (!switchVersionListener.nodeSwitch(VaadinRequest.getCurrent(),
                    VaadinResponse.getCurrent())) {
                return;
            }

            // Do application level clean-up before version switch
            switchVersionListener.doAppCleanup();
        }

        // When the user clicks on the notificator remove the cluster
        // and session cookies
        removeCookie(STICKY_CLUSTER_COOKIE);
        removeCookie(CURRENT_VERSION_COOKIE);
        removeCookie(UPDATE_VERSION_COOKIE);

        // Invalidate the session, Vaadin does not synchronizes it
        // between clusters
        VaadinRequest.getCurrent().getWrappedSession().invalidate();
    }

    private Cookie getCookieByName(String cookieName) {
        VaadinRequest request = VaadinRequest.getCurrent();
        if (cookieName == null || request == null
                || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }

    private void removeCookie(String cookieName) {
        getLogger().debug("Removing cookie '{}'.", cookieName);
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setMaxAge(0);
        VaadinResponse.getCurrent().addCookie(cookie);
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(ClusterSupport.class);
    }
}
