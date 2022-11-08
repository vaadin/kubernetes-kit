package com.vaadin.azure.starter.sessiontracker.serialization;

import java.util.List;

/**
 * Implementors of this interface are responsible for inspecting objects to
 * gather information about transient fields instances to be able to inject them
 * again on different instances.
 *
 * Field values should be encoded into a reference {@literal identifier} by
 * {@link #inspect(Object)} method, in a format that allows
 * {@link #inject(Object, List)} to inject the correct instances later on.
 */
public interface TransientHandler {

    /**
     * Inspects an object for injectable transient fields and returns a
     * description of the field and a symbolic reference of the instance in the
     * form of {@link TransientDescriptor} objects.
     *
     * If no eligible transient fields are found, the method must return an
     * empty list.
     *
     * @param object
     *            object to be inspected for transient fields.
     * @return transient fields descriptors, never {@literal null}.
     */
    List<TransientDescriptor> inspect(Object object);

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
