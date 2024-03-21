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

import java.io.Serializable;
import java.util.function.ToIntFunction;

/**
 * Container of information about serialized objects.
 */
public final class Track implements Serializable {
    /**
     * The unique identifier of the serialized object in the stream.
     */
    public final int id;
    /**
     * Object graph depth.
     */
    public final int depth;
    /**
     * Path to the object in the object graph.
     */
    public final String stackInfo;
    /**
     * Identifier of the instance inside the references table.
     *
     * Every serialized instance is stored in a table with a unique identifier,
     * so if the object is referenced multiple times in the object graph it can
     * be replaced by its handle when writing the stream. Since this information
     * is written into the stream, it can be used during deserialization to look
     * up an already deserialized object.
     */
    private int handle = -1;

    /**
     * Type of the Object being tracked.
     */
    public final String className;

    /**
     * Object being tracked.
     */
    transient Object object;

    public Track(int id, int depth, String stackInfo, Object object) {
        this.id = id;
        this.depth = depth;
        this.stackInfo = stackInfo;
        this.object = object;
        this.className = (object != null) ? object.getClass().getName()
                : "NULL";
    }

    private Track(int depth, Class<?> type) {
        this.id = -1;
        this.depth = depth;
        this.stackInfo = null;
        this.object = null;
        this.className = type.getName();
    }

    /**
     * Gets the handle of the tracked object in the references table.
     *
     * @return the handle of the tracked object, or -1 if the object is not
     *         present in the references table.
     */
    public int getHandle() {
        return handle;
    }

    /**
     * Associate the tracking object with instance handle.
     *
     * @param handleLookup
     *            a function that gets the handle of the current object.
     * @return this instance, for chaining.
     */
    public Track assignHandle(ToIntFunction<Object> handleLookup) {
        if (object != null) {
            this.handle = handleLookup.applyAsInt(object);
        }
        return this;
    }

    public Track withEstimatedDepth(int depth) {
        return new Track(id, depth, null, object);
    }

    public Track withEstimatedHandle(int handle) {
        Track track = new Track(id, depth, null, object);
        track.handle = handle;
        return track;
    }

    public static Track unknown(int depth, Class<?> type) {
        return new Track(depth, type);
    }
}
