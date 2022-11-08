package com.vaadin.azure.starter.sessiontracker.serialization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A serializable class that holds information about an object to be
 * serialized/deserialized and its transient fields that can be injected after
 * deserialization.
 *
 * For internal use only.
 */
final class TransientAwareHolder implements Serializable {

    private final List<TransientDescriptor> transientDescriptors;
    private final Object source;

    TransientAwareHolder(Object source, List<TransientDescriptor> descriptors) {
        this.source = source;
        this.transientDescriptors = new ArrayList<>(descriptors);
    }

    /**
     * Gets the list of descriptor of transient fields capable to be injected
     * after deserialization.
     * 
     * @return list of injectable transient fields descriptors.
     */
    List<TransientDescriptor> transients() {
        return new ArrayList<>(transientDescriptors);
    }

    /**
     * Gets the object to be serialized and deserialized.
     * 
     * @return object to be serialized and deserialized.
     */
    Object source() {
        return source;
    }

}
