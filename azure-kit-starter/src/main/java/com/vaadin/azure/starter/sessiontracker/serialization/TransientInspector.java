package com.vaadin.azure.starter.sessiontracker.serialization;

import java.util.List;

/**
 * Component responsible for inspecting objects to gather information about
 * transient fields.
 *
 * Field value should be encoded into a reference understandable by a
 * {@link TransientInjector}, so that instances can be injected into different
 * instance later on.
 */
public interface TransientInspector {

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

}
