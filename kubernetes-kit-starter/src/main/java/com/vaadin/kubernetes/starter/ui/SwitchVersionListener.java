/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.ui;

import java.io.Serializable;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;

/**
 * Interface for receiving events on version change.
 */
public interface SwitchVersionListener extends Serializable {

    /**
     * Notify about the cluster node change to allow graceful transition of the
     * users. <b>Note:</b> Even returning <code>false</code>, the application
     * might still be shut down by the environment.
     *
     * @param vaadinRequest
     *            Vaadin request when the change is initiated.
     * @param vaadinResponse
     *            Response from the server to be sent to the client.
     * @return <code>true</code> if the cluster node change is ok (default
     *         value), <code>false</code> if the change should not be performed.
     */
    default boolean nodeSwitch(VaadinRequest vaadinRequest,
            VaadinResponse vaadinResponse) {
        return true;
    }

    /**
     * Makes possible to do application level clean-up before the version
     * switch.
     */
    default void doAppCleanup() {
    }
}
