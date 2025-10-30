package com.vaadin.kubernetes.starter.sessiontracker;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Optional;

public interface SessionTracker {

    void sessionCreated(String clusterKey, HttpSession session,
            HttpServletRequest request);

    /**
     * Retrieves the session ID mapped to the given cluster key, if available.
     * <p>
     * It also
     *
     * @param clusterKey
     *            the key used to identify a specific cluster in the session
     *            context
     * @return an {@code Optional} containing the session ID associated with the
     *         given cluster key, or an empty {@code Optional} if no mapping
     *         exists
     */
    Optional<String> mapSession(String clusterKey, HttpServletRequest request);

    void stop();

}
