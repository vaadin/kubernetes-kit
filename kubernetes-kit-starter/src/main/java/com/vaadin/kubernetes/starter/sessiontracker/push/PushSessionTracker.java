/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker.push;

import jakarta.servlet.http.HttpSession;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceSession;
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
 * {@link com.vaadin.kubernetes.starter.sessiontracker.SessionTrackerFilter} but
 * for PUSH communication.
 */
public class PushSessionTracker implements PushSendListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushSessionTracker.class);

    private final SessionSerializer sessionSerializer;

    private Predicate<String> activeSessionChecker = id -> true;
    private String clusterCookieName;

    /**
     * @deprecated use {@link #PushSessionTracker(SessionSerializer, String)}
     *             instead
     */
    @Deprecated(forRemoval = true)
    public PushSessionTracker(SessionSerializer sessionSerializer) {
        this.sessionSerializer = sessionSerializer;
        this.clusterCookieName = CurrentKey.COOKIE_NAME;
    }

    public PushSessionTracker(SessionSerializer sessionSerializer,
            String clusterCookieName) {
        this.sessionSerializer = sessionSerializer;
        this.clusterCookieName = clusterCookieName;
    }

    /**
     * Sets the active HTTP session checker.
     *
     * @param activeSessionChecker
     *            active HTTP session checker.
     */
    public void setActiveSessionChecker(
            Predicate<String> activeSessionChecker) {
        this.activeSessionChecker = Objects.requireNonNull(activeSessionChecker,
                "session checker must not be null");
    }

    @Override
    public boolean canPush() {
        return sessionSerializer.isRunning();
    }

    @Override
    public void onConnect(AtmosphereResource resource) {
        // The HTTP request associate to the resource might not be available
        // after connection for example because recycled by the servlet
        // container.
        // To be able to always get the correct cluster key, it is fetched
        // during connection and stored in the atmosphere resource session.
        AtmosphereResourceSession resourceSession = resource
                .getAtmosphereConfig().sessionFactory().getSession(resource);
        tryGetSerializationKey(resource).ifPresent(key -> resourceSession
                .setAttribute(CurrentKey.COOKIE_NAME, key));
    }

    @Override
    public void onMessageSent(AtmosphereResource resource) {
        HttpSession httpSession = resource.session(false);
        if (httpSession == null) {
            LOGGER.debug(
                    "Skipping session serialization. HTTP session not available");
        } else if (!activeSessionChecker.test(httpSession.getId())) {
            LOGGER.debug(
                    "Skipping session serialization. HTTP session {} not active",
                    httpSession.getId());
        } else {
            tryGetSerializationKey(resource).ifPresent(CurrentKey::set);
            if (CurrentKey.get() != null) {
                LOGGER.debug("Serializing session {} with key {}",
                        httpSession.getId(), CurrentKey.get());
                try {
                    sessionSerializer.serialize(httpSession);
                } finally {
                    CurrentKey.clear();
                }
            } else {
                LOGGER.debug(
                        "Skipping session serialization. Missing serialization key.");
            }
        }
    }

    private Optional<String> tryGetSerializationKey(
            AtmosphereResource resource) {

        AtmosphereResourceSession resourceSession = resource
                .getAtmosphereConfig().sessionFactory()
                .getSession(resource, false);
        HttpSession httpSession = resource.session(false);
        String key = null;
        if (resourceSession != null) {
            key = resourceSession.getAttribute(CurrentKey.COOKIE_NAME,
                    String.class);
        }
        if (key == null) {
            try {
                key = SessionTrackerCookie
                        .getValue(resource.getRequest().wrappedRequest(),
                                clusterCookieName)
                        .orElse(null);
            } catch (Exception ex) {
                LOGGER.debug("Cannot get serialization key from request",
                        ex);
            }
        }
        if (key == null && httpSession != null) {
            try {
                key = SessionTrackerCookie.getFromSession(httpSession)
                        .orElse(null);
            } catch (Exception ex) {
                LOGGER.debug("Cannot get serialization key from session",
                        ex);
            }
        }
        return Optional.ofNullable(key);
    }

}
