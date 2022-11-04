package com.vaadin.azure.starter.ui;

import javax.servlet.http.Cookie;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.server.RequestHandler;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.shared.Registration;

/**
 * Cluster support for Vaadin applications.
 * This component allows receiving events from the cluster running the Vaadin
 * application.
 */
public class ClusterSupport implements VaadinServiceInitListener {

    private static final long serialVersionUID = 1L;

    /** Configuration property name. */
    public static final String PROPERTY_APP_VERSION = "app.version";
    public static final String ENV_APP_VERSION = "APP_VERSION";

    /* Control cookie name. */
    public static final String CURRENT_VERSION_COOKIE = "app-version";
    public static final String UPDATE_VERSION_COOKIE = "app-update";
    public static final String STICKY_CLUSTER_COOKIE = "INGRESSCOOKIE";

    /* Global listener for version changes. */
    private SwitchVersionListener switchVersionListener;

    private String appVersion;

    @Tag("version-notificator")
    @JsModule("./components/version-notificator.ts")
    public static class VersionNotificator extends Component {
        private static final long serialVersionUID = 1L;

        VersionNotificator(String current, String update) {
            getElement().setProperty("currentVersion", current);
            getElement().setProperty("updateVersion", update);
        }

        public Registration addSwitchVersionEvent(
                ComponentEventListener<SwitchVersionEvent> listener) {
            getLogger().debug("Adding listener for SwitchVersionEvent.");
            return addListener(SwitchVersionEvent.class, listener);
        }

        @DomEvent("load-version")
        public static class SwitchVersionEvent
                extends ComponentEvent<VersionNotificator> {
            private static final long serialVersionUID = 1L;

            public SwitchVersionEvent(VersionNotificator source,
                                      boolean fromClient) {
                super(source, fromClient);
            }
        }
    }

    /**
     * Interface for receiving events on cluster change.
     */
    public interface SwitchVersionListener extends Serializable {

        /**
         * Notify about the cluster node change to allow graceful transition of
         * the users.
         * <b>Note:</b> Even returning <code>false</code>, the application might
         * still be shut down by the environment.
         *
         * @param vaadinRequest
         *            Vaadin request when the change is initiated.
         * @param vaadinResponse
         *            Response from the server to be sent to the client.
         * @return <code>@code true</code> if the cluster change is ok, false if
         *         the change should not be performed.
         */
        boolean clusterSwitch(VaadinRequest vaadinRequest,
                              VaadinResponse vaadinResponse);
    }

    /**
     * Register the version switch listener.
     *
     * @param listener
     *            SwitchVersionListener to register.
     */
    public void setSwitchVersionListener(SwitchVersionListener listener) {
        this.switchVersionListener = listener;
    }

    private Cookie getCookieByName(String cookieName) {
        VaadinRequest request = VaadinRequest.getCurrent();
        if (cookieName == null || request == null
                || request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (cookieName.equals(c.getName())) {
                return c;
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

    public static ClusterSupport getCurrent() {
        return CurrentInstance.get(ClusterSupport.class);
    }

    @Override
    public void serviceInit(ServiceInitEvent serviceInitEvent) {
        // Environment variable has the highest priority
        appVersion = System.getenv(ENV_APP_VERSION);
        // Try the Spring way if spring is in the class-path
        if (appVersion == null) {
            try {
                // Use reflection for figuring out whether the project is using
                // spring
                String clzName = this.getClass().getPackage().getName()
                        + ".spring.SpringAppVersion";
                Class<?> clz = Class.forName(clzName);
                Method method = clz.getDeclaredMethod("getAppVersion");
                appVersion = (String) method.invoke(null);
            } catch (ClassNotFoundException | NoSuchMethodException
                     | SecurityException | IllegalAccessException
                     | IllegalArgumentException
                     | InvocationTargetException ignore) {
                // Intentionally ignored, not a spring application
            }
        }
        // Finally use System.properties
        if (appVersion == null) {
            appVersion = serviceInitEvent.getSource()
                    .getDeploymentConfiguration()
                    .getApplicationOrSystemProperty(PROPERTY_APP_VERSION, null,
                            v -> v);
        }
        if (appVersion == null) {
            getLogger().error(
                    "Missing property 'app.version' or environment variable 'APP_VERSION'. ClusterSupport service not initialized.");
            return;
        }
        getLogger().info(
                "ClusterSupport service initialized. Registering RequestHandler with Application Version: "
                        + appVersion);

        // Register a generic request handler for all the requests.
        serviceInitEvent.addRequestHandler((RequestHandler) (vaadinSession,
                                                             vaadinRequest, vaadinResponse) -> {

            // Set the thread local instance
            CurrentInstance.set(ClusterSupport.class, this);

            vaadinSession.access(() -> {

                // Set always the version cookie
                Cookie versionCookie = getCookieByName(CURRENT_VERSION_COOKIE);
                if (versionCookie == null
                        || !versionCookie.getValue().equals(appVersion)) {
                    versionCookie = new Cookie(CURRENT_VERSION_COOKIE,
                            appVersion);
                    versionCookie.setHttpOnly(true);
                    vaadinResponse.addCookie(versionCookie);
                }

                // Check always for a new version cookie
                Cookie currentCookie = getCookieByName(UPDATE_VERSION_COOKIE);
                if (currentCookie != null && !currentCookie.getValue().isEmpty()
                        && !versionCookie.getValue()
                        .equals(currentCookie.getValue())) {
                    vaadinSession.getUIs().forEach(ui -> {
                        if (ui.getChildren().anyMatch(
                                c -> (c instanceof VersionNotificator))) {
                            return;
                        }
                        VersionNotificator notificator = new VersionNotificator(
                                appVersion, currentCookie.getValue());
                        notificator.addSwitchVersionEvent(e -> {

                            // Do nothing if switch version listener prevents
                            // switching
                            if (switchVersionListener != null
                                    && !switchVersionListener.clusterSwitch(
                                    VaadinRequest.getCurrent(), VaadinResponse.getCurrent())) {
                                return;
                            }
                            // When the users click on the notificator remove
                            // cluster and session cookies
                            removeCookie(STICKY_CLUSTER_COOKIE);
                            removeCookie(CURRENT_VERSION_COOKIE);
                            removeCookie(UPDATE_VERSION_COOKIE);
                            // Invalidate session, vaadin does not synchronizes
                            // it between clusters
                            VaadinRequest.getCurrent().getWrappedSession()
                                    .invalidate();
                        });

                        // Show notificator
                        ui.add(notificator);
                    });
                }

            });

            // If current and new version does not match, notify user
            return false;
        });
    }

    /**
     * Get the application version set when deployed, via the enviroment
     * variable APP_VERSION, and the system or application property app.version
     *
     * @return appVersion
     */
    public String getAppVersion() {
        return appVersion;
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(ClusterSupport.class);
    }
}
