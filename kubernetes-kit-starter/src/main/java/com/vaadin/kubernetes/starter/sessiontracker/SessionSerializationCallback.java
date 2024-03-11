package com.vaadin.kubernetes.starter.sessiontracker;

/**
 * Callbacks that are called after a successful serialization and
 * deserialization or when an error happens during the serialization or
 * deserialization. Each callback has a default method implementation. A bean
 * needs to be created from a class implementing this interface.
 */
public interface SessionSerializationCallback {

    /**
     * The default implementation of this interface, which is used when there is
     * no bean provided from other implementation.
     */
    SessionSerializationCallback DEFAULT = new SessionSerializationCallback() {
    };

    /**
     * Callback that is called after a successful serialization.
     */
    default void onSerializationSuccess() {
    }

    /**
     * Callback that is called when a serialization error happens. Should not
     * throw any exception.
     *
     * @param exception
     *            the exception that is the cause of the serialization error
     */
    default void onSerializationError(Exception exception) {
    }

    /**
     * Callback that is called after a successful deserialization.
     */
    default void onDeserializationSuccess() {
    }

    /**
     * Callback that is called when a deserialization error happens. Should not
     * throw any exception.
     *
     * @param exception
     *            the exception that is the cause of the deserialization error.
     */
    default void onDeserializationError(Exception exception) {
    }
}
