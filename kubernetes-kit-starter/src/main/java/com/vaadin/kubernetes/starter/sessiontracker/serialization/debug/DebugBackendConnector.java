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

import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import org.slf4j.Logger;

import com.vaadin.kubernetes.starter.sessiontracker.backend.BackendConnector;
import com.vaadin.kubernetes.starter.sessiontracker.backend.SessionInfo;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientHandler;

/**
 * A dummy {@link BackendConnector} implementations that locally stores the
 * serialized data.
 *
 * It is meant to only be used with the Kubernetes Kit Debug Tool.
 */
class DebugBackendConnector implements BackendConnector,
        BiFunction<String, String, TransientHandler> {

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, SessionInfo> serializedSessions = new ConcurrentHashMap<>();
    private final Map<Job, DebugTransientHandler> handlers = new IdentityHashMap<>();

    @Override
    public void sendSession(SessionInfo sessionInfo) {
        if (sessionInfo != null) {
            serializedSessions.put(sessionInfo.getClusterKey(), sessionInfo);
        }
    }

    @Override
    public SessionInfo getSession(String clusterKey) {
        getJob(clusterKey);
        return serializedSessions.get(clusterKey);
    }

    @Override
    public void deleteSession(String clusterKey) {
        serializedSessions.remove(clusterKey);
        Job job = jobs.remove(clusterKey);
        if (job != null) {
            job.reset();
            handlers.remove(job);
        }
    }

    @Override
    public void markSerializationStarted(String clusterKey) {
        getJob(clusterKey).serializationStarted();
    }

    @Override
    public void markSerializationStarted(String clusterKey,
            Duration timeToLive) {
        getJob(clusterKey).serializationStarted();
    }

    @Override
    public void markSerializationComplete(String clusterKey) {
        Job job = getJob(clusterKey);
        job.serialized(serializedSessions.get(clusterKey));
    }

    @Override
    public void markSerializationFailed(String clusterKey, Throwable error) {
        Job job = getJob(clusterKey);
        job.serializationFailed(new Exception(error));
    }

    @Override
    public TransientHandler apply(String sessionId, String clusterKey) {
        return handlers.computeIfAbsent(getJob(clusterKey),
                DebugTransientHandler::new);
    }

    /**
     * Gets a new Job for the current session and cluster key, or an empty
     * {@link Optional} if there is a job already in progress.
     *
     * @param sessionId
     *            the session id
     * @param clusterKey
     *            the cluster key
     * @return a new Job for the current session and cluster key, or an empty
     *         {@link Optional} if there is a job already in progress.
     */
    synchronized Optional<Job> newJob(String sessionId, String clusterKey) {
        if (!jobs.containsKey(clusterKey) && jobs.values().stream()
                .noneMatch(j -> j.isRunning(sessionId))) {
            Job job = new Job(sessionId, clusterKey);
            jobs.put(clusterKey, job);
            return Optional.of(job);
        }
        return Optional.empty();
    }

    void shutdown() {
        jobs.values().forEach(Job::cancel);
    }

    private Job getJob(String clusterKey) {
        Job job = jobs.get(clusterKey);
        if (job == null) {
            throw new IllegalStateException(
                    "No job started for clusterKey " + clusterKey);
        }
        return job;
    }

    /**
     * Blocks the thread for up to the defined timeout in milliseconds, waiting
     * for serialization to be completed.
     *
     * @param job
     *            the serialization job to wait for.
     * @param timeout
     *            the timeout in milliseconds to wait for the serialization to
     *            be completed.
     * @param logger
     *            the logger to add potential error information.
     * @return the serialized session holder.
     */
    SessionInfo waitForCompletion(Job job, int timeout, Logger logger) {
        job.waitForSerializationCompletion(timeout, logger);
        String clusterKey = jobs.entrySet().stream()
                .filter(e -> job == e.getValue()).map(Map.Entry::getKey)
                .findFirst().orElseThrow(
                        () -> new IllegalStateException("Job is not active"));
        return serializedSessions.get(clusterKey);
    }

}
