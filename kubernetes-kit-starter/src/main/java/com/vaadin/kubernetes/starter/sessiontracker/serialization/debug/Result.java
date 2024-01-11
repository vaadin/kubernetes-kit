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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represent the result of the test serialization/deserialization process.
 */
public class Result implements Serializable {

    public static final String CATEGORY_ERRORS = "ERRORS";

    private final String sessionId;
    private final String storageKey;
    private final LinkedHashSet<Outcome> outcomes;
    private final long duration;
    private final Map<String, List<String>> messages;

    Result(String sessionId, String storageKey, Set<Outcome> outcomes,
            long duration, Map<String, List<String>> messages) {
        this.sessionId = sessionId;
        this.storageKey = storageKey;
        this.outcomes = new LinkedHashSet<>(outcomes);
        this.duration = duration;
        this.messages = new LinkedHashMap<>(messages);
    }

    /**
     * Gets the identifier of the HTTP session under test.
     *
     * @return the identifier of the HTTP session under test.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Gets the identifier of the session data on the storage backend.
     *
     * @return the identifier of the session data on the storage backend.
     */
    public String getStorageKey() {
        return storageKey;
    }

    /**
     * Gets the outcome of the serialization/deserialization process.
     *
     * @return the outcome of the serialization/deserialization process.
     */
    public Set<Outcome> getOutcomes() {
        return outcomes;
    }

    /**
     * Gets the approximate duration of the process expressed in milliseconds.
     *
     * @return approximate duration of the process expressed in milliseconds.
     */
    public long getDuration() {
        return duration;
    }

    /**
     * Gets the list of not serializable classes detected.
     *
     * @return list of not serializable classes detected.
     */
    public List<String> getNotSerializableClasses() {
        return new ArrayList<>(
                messages.getOrDefault(Outcome.NOT_SERIALIZABLE_CLASSES.name(),
                        Collections.emptyList()));
    }

    /**
     * Gets the list of not serialized lambda classes detected.
     *
     * @return list of not serialized lambda detected.
     */
    public Set<String> getSerializedLambdas() {
        return new LinkedHashSet<>(
                messages.getOrDefault(Outcome.NOT_SERIALIZABLE_CLASSES.name(),
                        Collections.emptyList()));
    }

    /**
     * Gets serialization process errors.
     *
     * @return serialization process errors.
     */
    public List<String> getErrors() {
        return messages.getOrDefault(CATEGORY_ERRORS, Collections.emptyList());
    }
}
