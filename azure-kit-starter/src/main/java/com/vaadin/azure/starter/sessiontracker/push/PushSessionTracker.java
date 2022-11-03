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
        getLogger().info("PUSH message sent. HTTP session {}",
                (session == null) ? "NULL" : session.getClass());
        if (session != null) {
            SessionTrackerCookie
                    .getValue(resource.getRequest().wrappedRequest())
                    .ifPresent(CurrentKey::set);
            getLogger().info("PUSH message sent. Current Key  {}",
                    CurrentKey.get());
            try {
                sessionSerializer.serialize(session);
            } finally {
                CurrentKey.clear();
            }
        }
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(getClass());
    }

}
