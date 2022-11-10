package com.vaadin.azure.starter.sessiontracker.serialization;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Holds transient field details and a symbolic reference to the actual value.
 */
public final class TransientDescriptor implements Serializable {
    private final Class<?> declaringClass;
    private final String name;
    private final Class<?> type;

    private final String instanceReference;

    public TransientDescriptor(Field field, String reference) {
        declaringClass = field.getDeclaringClass();
        name = field.getName();
        type = field.getType();
        instanceReference = reference;
    }

    // Visible for test
    TransientDescriptor(Class<?> declaringClass, String name, Class<?> type,
            String instanceReference) {
        this.declaringClass = declaringClass;
        this.name = name;
        this.type = type;
        this.instanceReference = instanceReference;
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
                && instanceReference.equals(that.instanceReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(declaringClass, name, type, instanceReference);
    }

    @Override
    public String toString() {
        return String.format(
                "TransientDescriptor { field: %s.%s, type: %s, instance: %s }",
                declaringClass, name, type, instanceReference);
    }
}
