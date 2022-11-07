package com.vaadin.azure.starter.sessiontracker.push;

import javax.servlet.http.HttpSession;

import org.atmosphere.cpr.AtmosphereResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.azure.starter.sessiontracker.CurrentKey;
import com.vaadin.azure.starter.sessiontracker.SessionSerializer;
import com.vaadin.azure.starter.sessiontracker.SessionTrackerCookie;

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
