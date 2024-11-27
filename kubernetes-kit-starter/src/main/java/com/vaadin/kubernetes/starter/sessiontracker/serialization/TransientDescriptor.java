/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Holds transient field details and a symbolic reference to the actual value.
 */
public final class TransientDescriptor implements Serializable {

    @Serial
    private static final long serialVersionUID = 3577574582136843045L;

    private final Class<?> declaringClass;
    private final String name;
    private final Class<?> type;
    private final String instanceReference;
    private final boolean vaadinScoped;

    public TransientDescriptor(Field field, String reference) {
        this(field, reference, false);
    }

    public TransientDescriptor(Field field, String reference,
            boolean vaadinScoped) {
        declaringClass = field.getDeclaringClass();
        name = field.getName();
        type = field.getType();
        instanceReference = reference;
        this.vaadinScoped = vaadinScoped;
    }

    // Visible for test
    TransientDescriptor(Class<?> declaringClass, String name, Class<?> type,
            String instanceReference, boolean vaadinScoped) {
        this.declaringClass = declaringClass;
        this.name = name;
        this.type = type;
        this.instanceReference = instanceReference;
        this.vaadinScoped = vaadinScoped;
    }

    /**
     * Gets the class that declares the transient field. Gets the Class object
     * representing the class or interface that declares the transient field.
     *
     * @return transient field declaring class.
     */
    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    /**
     * Gets the Class object that identifies the declared type for the transient
     * field.
     *
     * @return transient field type
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Gets the name of the transient field.
     *
     * @return the name of the transient field
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the reference to the field instance value.
     *
     * The reference syntax depends on the {@link TransientHandler} that created
     * this descriptor instance. For example, for Spring it may be the name of a
     * managed bean.
     *
     * @return the reference to the field instance value
     */
    public String getInstanceReference() {
        return instanceReference;
    }

    /**
     * Gets if the instance value needs Vaadin thread locals to be set during
     * injection phase.
     *
     * @return {@literal true} is Vaadin thread locals are required to perform
     *         injection, otherwise {@literal false}.
     */
    boolean isVaadinScoped() {
        return vaadinScoped;
    }

    /**
     * Gets the Field object for the transient field.
     *
     * @return the Field object for the transient field.
     */
    public Field getField() {
        try {
            return declaringClass.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TransientDescriptor that = (TransientDescriptor) o;
        return declaringClass.equals(that.declaringClass)
                && name.equals(that.name) && type.equals(that.type)
                && instanceReference.equals(that.instanceReference)
                && vaadinScoped == that.vaadinScoped;
    }

    @Override
    public int hashCode() {
        return Objects.hash(declaringClass, name, type, instanceReference,
                vaadinScoped);
    }

    @Override
    public String toString() {
        return String.format(
                "TransientDescriptor { field: %s.%s, type: %s, instance: %s, vaadinScope: %s }",
                declaringClass, name, type, instanceReference, vaadinScoped);
    }
}
