package com.vaadin.kubernetes.starter.sessiontracker.serialization.debug;

import java.io.Serializable;
import java.util.Optional;

import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientHandler;

/**
 * Interface that may be implemented by {@link TransientHandler}s to debug the
 * serialization process.
 */
public interface DebugMode {
    /**
     * Hook invoked when a not {@link Serializable} object is found during
     * serialization process.
     *
     * Implementors can provide a serializable replacement object to prevent
     * {@link java.io.NotSerializableException} or an empty {@link Optional} to
     * continue processing with the input object.
     *
     * If an empty optional is returned, the serialization process will continue
     * and fail fast on input Object with a
     * {@link java.io.NotSerializableException}.
     *
     * To proceed the serialization process ignoring the current object,
     * implementor can return the special value {@link DebugMode#NULLIFY}. This
     * will inform the serialization process to replace the input object with
     * {@literal null} value, avoiding {@link java.io.NotSerializableException}.
     * In this modality, object fields will be ignored and not serialized.
     *
     * Default strategy is to continue processing with the current object and
     * let the process fail with {@link java.io.NotSerializableException}.
     *
     * @param object
     *            the not serializable object instance, never {@literal null}.
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
     * NOTE: implementation is not supposed not alter the input object, nor to
     * throw any kind on exception.
     *
     * @param object
     *            object that is going to be serialized.
     */
    default Object onSerialize(Object object, Track track) {
        // NO-OP
        return object;
    }

    /**
     * Hook notified when deserialization of an object of give type is started.
     *
     * @param type
     *            Java type of the object to deserialize.
     * @param track
     *            The tracking information for object to deserialize.
     */
    default void onDeserialize(Class<?> type, Track track) {
        // NO-OP
    }

    /**
     * Tracks an object right after deserialization
     *
     * Implementors can provide a replacement for current processing object.
     *
     * By default, the input object is returned.
     *
     * @param object
     *            object that is going to be serialized.
     */
    default Object onDeserialized(Object object, Track track) {
        return object;
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
