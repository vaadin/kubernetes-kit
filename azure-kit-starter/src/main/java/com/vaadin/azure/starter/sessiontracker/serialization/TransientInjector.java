package com.vaadin.azure.starter.sessiontracker.serialization;

import java.util.List;

/**
 * Component responsible to inject values into transient fields.
 */
public interface TransientInjector {

    /**
     * Injects values into the transient fields of given object.
     * 
     * @param object
     *            object target of injection, never {@literal null}.
     * @param transients
     *            descriptors of transient fields that should be injected, never
     *            {@literal null}.
     */
    void inject(Object object, List<TransientDescriptor> transients);
}
