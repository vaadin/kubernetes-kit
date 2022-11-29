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

import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Optional;

import org.slf4j.Logger;

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
    default void onDeserialize(Class<?> type, Track track, Object object) {
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

    /**
     * Checks if serialization object tracking is available on this JVM.
     *
     * For it to be enabled two pre-conditions must match:
     * <ul>
     * <li>sun.io.serialization.extendedDebugInfo system property must be set to
     * true</li>
     * <li>reflection on {@code java.io} package must be allowed through the
     * `--add-opens` flag (e.g. '--add-opens java.base/java.io=ALL-UNNAMED')
     * </li>
     * </ul>
     *
     * @param logger
     *            logger to print hints when preconditions are not met. May be
     *            {@literal null} if log is not required.
     * @return {@literal true} if extend debug info is activated, otherwise
     *         {@literal false}.
     */
    static boolean isTrackingAvailable(Logger logger) {
        boolean extendedDebugInfo = Boolean
                .getBoolean("sun.io.serialization.extendedDebugInfo");
        boolean logNotMetPreconditions = logger != null;
        if (!extendedDebugInfo && logNotMetPreconditions) {
            logger.warn(
                    "Serialization and deserialization traces cannot be detected if "
                            + "-Dsun.io.serialization.extendedDebugInfo system property is not enabled. "
                            + "Please add '-Dsun.io.serialization.extendedDebugInfo=true' to the JVM arguments.");
        }
        boolean canAccessJavaIO = ObjectOutputStream.class.getModule().isOpen(
                ObjectOutputStream.class.getPackageName(),
                DebugMode.class.getModule());
        if (!canAccessJavaIO && logNotMetPreconditions) {
            logger.warn(
                    "Reflection on ObjectInputStream and ObjectOutputStream classes is required for session serialization debug. "
                            + "Please open java.io to ALL-UNNAMED module, adding "
                            + "'--add-opens java.base/java.io=ALL-UNNAMED' to the JVM arguments.");
        }
        return extendedDebugInfo && canAccessJavaIO;
    }

    /**
     * Silently checks if serialization object tracking is available on this
     * JVM.
     *
     * @see #isTrackingAvailable(Logger)
     */
    static boolean isTrackingAvailable() {
        return isTrackingAvailable(null);
    }

}
