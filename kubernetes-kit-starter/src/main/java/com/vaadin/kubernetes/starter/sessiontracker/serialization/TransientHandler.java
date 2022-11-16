package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

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

    /**
     * Interface that may be implemented by {@link TransientHandler}s to debug
     * the serialization process.
     */
    interface DebugMode {
        /**
         * Hook invoked when a not {@link Serializable} object is found during
         * serialization process.
         *
         * Implementors can provide a serializable replacement object to prevent
         * {@link java.io.NotSerializableException} or an empty {@link Optional}
         * to continue processing with the input object.
         *
         * If an empty optional is returned, the serialization process will
         * continue and fail fast on input Object with a
         * {@link java.io.NotSerializableException}.
         *
         * To proceed the serialization process ignoring the current object,
         * implementor can return the special value {@link DebugMode#NULLIFY}.
         * This will inform the serialization process to replace the input
         * object with {@literal null} value, avoiding
         * {@link java.io.NotSerializableException}. In this modality, object
         * fields will be ignored and not serialized.
         *
         * Default strategy is to continue processing with the current object
         * and let the process fail with
         * {@link java.io.NotSerializableException}.
         *
         * @param object
         *            the not serializable object instance, never
         *            {@literal null}.
         * @return a serializable replacement for the input object or an empty
         *         Optional, never {@literal null}.
         * @see DebugMode#NULLIFY
         */
        default Optional<Serializable> onNotSerializableFound(Object object) {
            return Optional.empty();
        }

        /**
         * Tracks an object being serialized.
         *
         * NOTE: implementation is not supposed not alter the input object, nor
         * to throw any kind on exception.
         *
         * @param object
         *            object that is going to be serialized.
         */
        default void onSerialize(Object object) {
            // NO-OP
        }

        /**
         * Hook notified when deserialization of an object of give type is
         * started.
         *
         * @param type
         *            Java type of the object to deserialize.
         * @param depth
         *            The current depth. The depth starts at 1 and increases for
         *            each nested object and decrements when each nested object
         *            is completed.
         */
        default void onDeserialize(Class<?> type, long depth) {
            // NO-OP
        }

        /**
         * Tracks an object right after deserialization
         *
         * NOTE: implementation is not supposed not alter the input object, nor
         * to throw any kind on exception.
         *
         * @param object
         *            object that is going to be serialized.
         */
        default void onDeserialized(Object object) {
            // NO-OP
        }

        /**
         * A placeholder to indicate that a not serializable object should be
         * nullified during serialization process.
         */
        Serializable NULLIFY = new Serializable() {
            @Override
            public int hashCode() {
                return super.hashCode();
            }
        };
    }
}
