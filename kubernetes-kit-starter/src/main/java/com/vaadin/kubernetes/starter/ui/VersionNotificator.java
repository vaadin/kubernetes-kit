package com.vaadin.kubernetes.starter.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

/**
 * The version notificator component is used when there is a new version and the
 * update version cookie is set.
 */
@Tag("version-notificator")
@JsModule("./components/version-notificator.ts")
public class VersionNotificator extends Component {
    private static final long serialVersionUID = 1L;

    VersionNotificator(String current, String update) {
        getElement().setProperty("currentVersion", current);
        getElement().setProperty("updateVersion", update);
    }

    /**
     * Adds a listener to listen to switch version events.
     *
     * @param listener
     *            the listener to add.
     */
    public void addSwitchVersionEventListener(
            ComponentEventListener<SwitchVersionEvent> listener) {
        getLogger().debug("Adding listener for SwitchVersionEvent.");
        addListener(SwitchVersionEvent.class, listener);
    }

    /**
     * Event which is dispatched when the user accepts the version change
     * through the notificator.
     */
    @DomEvent("load-version")
    public static class SwitchVersionEvent
            extends ComponentEvent<VersionNotificator> {
        private static final long serialVersionUID = 1L;

        public SwitchVersionEvent(VersionNotificator source,
                boolean fromClient) {
            super(source, fromClient);
        }
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(VersionNotificator.class);
    }
}
