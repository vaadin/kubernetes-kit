/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker.serialization.debug;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.vaadin.kubernetes.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionInfo;

/**
 * A dummy {@link BackendConnector} implementations that locally stores the
 * serialized data.
 *
 * It is meant to only be used with the Kubernetes Kit Debug Tool.
 */
class DebugBackendConnector implements BackendConnector {

    private final Job job;

    public DebugBackendConnector(Job job) {
        this.job = job;
    }

    private SessionInfo sessionInfo;

    private CountDownLatch latch;

    @Override
    public void sendSession(SessionInfo sessionInfo) {
        this.sessionInfo = sessionInfo;
    }

    @Override
    public SessionInfo getSession(String clusterKey) {
        return sessionInfo;
    }

    @Override
    public void markSerializationStarted(String clusterKey) {
        latch = new CountDownLatch(1);
        job.serializationStarted();
    }

    @Override
    public void markSerializationComplete(String clusterKey) {
        job.serialized(sessionInfo);
        latch.countDown();
    }

    @Override
    public void deleteSession(String clusterKey) {
        // NO-OP
    }

    /**
     * Blocks the thread for up to the defined timeout in milliseconds, waiting
     * for serialization to be completed.
     *
     * @param timeout
     *            the timeout in milliseconds to wait for the serialization to
     *            be completed.
     * @param logger
     *            the logger to add potential error information.
     * @return the serialized session holder.
     */
    SessionInfo waitForCompletion(int timeout, Logger logger) {
        try {
            if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                job.timeout();
                logger.error(
                        "Session serialization timed out because did not complete in {} ms. "
                                + "Increase the serialization timeout (in milliseconds) by the "
                                + "'vaadin.serialization.timeout' application or system property.",
                        timeout);
            }
        } catch (Exception e) { // NOSONAR
            logger.error("Testing of session serialization failed", e);
        }
        return sessionInfo;
    }

}
