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

/**
 * Outcome of the serialization/deserialization process.
 */
public enum Outcome {
    /**
     * Process has not started.
     */
    NOT_STARTED,
    /**
     * Not serializable classes found during serialization phase
     */
    NOT_SERIALIZABLE_CLASSES,
    /**
     * Process failed during serialization phase
     */
    SERIALIZATION_FAILED,
    /**
     * Process failed during deserialization phase
     */
    DESERIALIZATION_FAILED,
    /**
     * Process completed successfully
     */
    SUCCESS
}
