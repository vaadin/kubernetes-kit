package com.vaadin.kubernetes.starter.sessiontracker.push;

import javax.servlet.http.HttpSession;

import org.atmosphere.cpr.AtmosphereResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.kubernetes.starter.sessiontracker.CurrentKey;
import com.vaadin.kubernetes.starter.sessiontracker.SessionSerializer;
import com.vaadin.kubernetes.starter.sessiontracker.SessionTrackerCookie;

/**
 * A {@link PushSendListener} that serialize HTTP session when messages are
 * pushed to the client.
 *
 * It has the same scope of
 * {@link com.vaadin.kubernetes.starter.sessiontracker.SessionTrackerFilter} but for
 * PUSH communication.
 */
public class PushSessionTracker implements PushSendListener {

    private final SessionSerializer sessionSerializer;

    public PushSessionTracker(SessionSerializer sessionSerializer) {
        this.sessionSerializer = sessionSerializer;
    }

    @Override
    public void onMessageSent(AtmosphereResource resource) {
        HttpSession session = resource.session(false);
        if (session != null && resource.getRequest().wrappedRequest()
                .isRequestedSessionIdValid()) {
            SessionTrackerCookie
                    .getValue(resource.getRequest().wrappedRequest())
                    .ifPresent(CurrentKey::set);
            getLogger().debug("Serializing session {} with key {}",
                    session.getId(), CurrentKey.get());
            try {
                sessionSerializer.serialize(session);
            } finally {
                CurrentKey.clear();
            }
        } else {
            getLogger().debug(
                    "Skipping session serialization. Session is invalidated");
        }
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(getClass());
    }

}
