package com.vaadin.kubernetes.starter.sessiontracker.backend;

import java.time.Duration;

/**
 * A rule that determines the expiration for a backend session based on the
 * current HTTP session timeout.
 */
public interface SessionExpirationPolicy {

    /**
     * Computes the maximum amount of time an inactive session should be
     * preserved in the backed, based on the given HTTP session timeout
     * expressed in seconds.
     * <p>
     * </p>
     * A return value of {@link Duration#ZERO} or less means the backend session
     * should never expire.
     * 
     * @param sessionTimeout
     *            HTTP session timeout expressed in seconds.
     * @return the maximum amount of time an inactive session should be
     *         preserved in the backed.
     */
    Duration apply(long sessionTimeout);

    /**
     * A policy that prevents expiration.
     */
    SessionExpirationPolicy NEVER = sessionTimeout -> Duration.ZERO;

}
