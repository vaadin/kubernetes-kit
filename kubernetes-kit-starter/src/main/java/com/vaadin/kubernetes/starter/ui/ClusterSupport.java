package com.vaadin.kubernetes.starter.ui;

import java.util.Optional;

import javax.servlet.http.Cookie;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Component;
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
     * Update version cookie name.
     */
    public static final String UPDATE_VERSION_HEADER = "X-AppUpdate";

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

        // Set the thread local instance
        CurrentInstance.set(ClusterSupport.class, this);

        // Register a generic request handler for all the requests
        serviceInitEvent.addRequestHandler(this::handleRequest);
    }

    private boolean handleRequest(VaadinSession vaadinSession,
            VaadinRequest vaadinRequest, VaadinResponse vaadinResponse) {

        vaadinSession.access(() -> {
            // Always check for the new version header
            String headerVersion = vaadinRequest
                    .getHeader(UPDATE_VERSION_HEADER);

            vaadinSession.getUIs().forEach(ui -> {
                Optional<Component> first = ui.getChildren()
                        .filter(c -> (c instanceof VersionNotificator))
                        .findFirst();
                if (first.isPresent()) {
                    // Remove notificator in a version roll-back or
                    // proxy is not setting the new version header
                    if (headerVersion == null || headerVersion.isEmpty()
                            || appVersion.equals(headerVersion)) {
                        ui.remove(first.get());
                    }
                } else if (headerVersion != null && !headerVersion.isEmpty()
                        && !appVersion.equals(headerVersion)) {
                    // Show notificator because versions do not match
                    VersionNotificator notificator = new VersionNotificator(
                            appVersion, headerVersion);
                    notificator.addSwitchVersionEventListener(
                            this::onComponentEvent);
                    ui.add(notificator);
                }
            });
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

        // When the user clicks on the notificator remove session cookies
        removeCookie(STICKY_CLUSTER_COOKIE);

        // Invalidate the session, Vaadin does not synchronizes it
        // between clusters
        VaadinRequest.getCurrent().getWrappedSession().invalidate();
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
