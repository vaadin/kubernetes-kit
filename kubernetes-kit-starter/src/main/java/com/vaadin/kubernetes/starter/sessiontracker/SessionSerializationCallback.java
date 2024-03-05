package com.vaadin.kubernetes.starter.sessiontracker;

/**
 * Error callbacks that are called after a serialization or deserialization
 * error happens.
 */
public interface SessionSerializationCallback {

    /**
     * Error callback that is called after a serialization error happens.
     */
    default void onSerializationError(Exception ex) {
    }

    /**
     * Error callback that is called after a deserialization error happens.
     */
    default void onDeserializationError(Exception ex) {
    }
}
