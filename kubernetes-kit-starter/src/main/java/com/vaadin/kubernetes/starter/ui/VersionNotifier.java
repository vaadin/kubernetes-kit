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
 * The version notifier component is used when there is a new version and the
 * update version cookie is set.
 */
@Tag("version-notifier")
@JsModule("./components/version-notifier.ts")
public class VersionNotifier extends Component {
    private static final long serialVersionUID = 1L;

    VersionNotifier(String current, String update) {
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
     * through the notifier.
     */
    @DomEvent("load-version")
    public static class SwitchVersionEvent
            extends ComponentEvent<VersionNotifier> {
        private static final long serialVersionUID = 1L;

        public SwitchVersionEvent(VersionNotifier source, boolean fromClient) {
            super(source, fromClient);
        }
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(VersionNotifier.class);
    }
}
