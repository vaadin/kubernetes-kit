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
 * Exception raise during session serialization when
 * {@link UnserializableComponentWrapper} is found in the UI tree to indicate
 * that VaadinSession lock is required to complete the operation.
 */
public class UnserializableComponentWrapperFoundException
        extends PessimisticSerializationRequiredException {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message
     *            the detail message. The detail message is saved for later
     *            retrieval by the {@link #getMessage()} method.
     */
    public UnserializableComponentWrapperFoundException(String message) {
        super(message);
    }
}
