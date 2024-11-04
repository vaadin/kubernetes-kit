/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker;

/**
 * Exception raise during session serialization to indicate that VaadinSession
 * lock is required to complete the operation.
 */
public class PessimisticSerializationRequiredException
        extends RuntimeException {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message
     *            the detail message. The detail message is saved for later
     *            retrieval by the {@link #getMessage()} method.
     */
    public PessimisticSerializationRequiredException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message
     *            the detail message.
     * @param cause
     *            the cause. (A {@code null} value is permitted, and indicates
     *            that the cause is nonexistent or unknown.)
     */
    public PessimisticSerializationRequiredException(String message,
            Throwable cause) {
        super(message, cause);
    }
}
