/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker.backend;

import java.time.Duration;

/**
 * Interface for backend storage connectors that handle session
 * serialization/deserialization lifecycle.
 * <p>
 * Implementations of this interface provide the underlying storage mechanism
 * for persisting serialized Vaadin session data. The interface supports both
 * session data storage and coordination mechanisms to track the serialization
 * lifecycle.
 */
public interface BackendConnector {
    /**
     * Stores serialized session data in the backend storage.
     *
     * @param sessionInfo
     *            the session information containing the cluster key, serialized
     *            data, and time-to-live settings.
     */
    void sendSession(SessionInfo sessionInfo);

    /**
     * Retrieves session data from the backend storage by cluster key.
     *
     * @param clusterKey
     *            the distributed storage key identifying the session.
     * @return the session information containing the serialized data, or
     *         {@code null} if no session is found for the given key.
     */
    SessionInfo getSession(String clusterKey);

    /**
     * Removes session data from the backend storage.
     *
     * @param clusterKey
     *            the distributed storage key identifying the session to delete.
     */
    void deleteSession(String clusterKey);

    /**
     * Marks the beginning of the serialization process for a session.
     * <p>
     * This method is used for coordination between multiple requests to
     * indicate that serialization is in progress and prevent concurrent access
     * issues. Implementors can decide if the method should block in case of
     * concurrent requests; potential acquired locks should be released in
     * {@link #markSerializationComplete(String)} and
     * {@link #markSerializationFailed(String, Throwable)} methods.
     *
     * @param clusterKey
     *            the distributed storage key identifying the session.
     * @param timeToLive
     *            the maximum amount of time the serialization marker should be
     *            preserved in the backend. A zero or negative value means the
     *            marker should not be evicted.
     */
    void markSerializationStarted(String clusterKey, Duration timeToLive);

    /**
     * Marks the successful completion of the serialization process for a
     * session.
     * <p>
     * This method is called after session data has been successfully serialized
     * and stored in the backend. Any lock has been acquired by
     * {@link #markSerializationStarted(String, Duration)} should be released
     * here.
     *
     * @param clusterKey
     *            the distributed storage key identifying the session.
     */
    void markSerializationComplete(String clusterKey);

    /**
     * Marks the serialization process as failed for a session.
     * <p>
     * This method is called when an error occurs during the serialization
     * process to record the failure and its cause. Any lock has been acquired
     * by {@link #markSerializationStarted(String, Duration)} should be released
     * here.
     *
     * @param clusterKey
     *            the distributed storage key identifying the session.
     * @param error
     *            the error that caused the serialization to fail.
     */
    void markSerializationFailed(String clusterKey, Throwable error);

    /**
     * Marks the beginning of the deserialization process for a session.
     * <p>
     * This method is used for coordination between multiple requests to
     * indicate that serialization is in progress and prevent concurrent access
     * issues. Implementors can decide if the method should block in case of
     * concurrent requests; potential acquired locks should be released in
     * {@link #markDeserializationComplete(String)} and
     * {@link #markDeserializationFailed(String, Throwable)} methods.
     *
     * @param clusterKey
     *            the distributed storage key identifying the session.
     * @param timeToLive
     *            the maximum amount of time the serialization marker should be
     *            preserved in the backend. A zero or negative value means the
     *            marker should not be evicted.
     * @return {@literal true} if there is no pending deserialization process,
     *         otherwise {@literal false}.
     */
    boolean markDeserializationStarted(String clusterKey, Duration timeToLive);

    /**
     * Marks the successful completion of the deserialization process for a
     * session.
     * <p>
     * This method is called after session data has been successfully
     * deserialized. Any lock that has been acquired by
     * {@link #markDeserializationStarted(String, Duration)} should be released
     * here.
     *
     * @param clusterKey
     *            the distributed storage key identifying the session.
     */
    void markDeserializationComplete(String clusterKey);

    /**
     * Marks the deserialization process as failed for a session.
     * <p>
     * This method is called when an error occurs during the serialization
     * process to record the failure and its cause. Any lock that has been
     * acquired by {@link #markDeserializationStarted(String, Duration)} should
     * be released here.
     *
     * @param clusterKey
     *            the distributed storage key identifying the session.
     * @param error
     *            the error that caused the serialization to fail.
     */
    void markDeserializationFailed(String clusterKey, Throwable error);

}
