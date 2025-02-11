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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.DebugMode;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.Track;

/**
 * An {@link ObjectOutputStream} implementation that adds to the binary stream
 * information about transient fields that can potentially be injected on
 * deserialization.
 *
 * It inspects instances of classes to get information about transient fields
 * and related instance identifiers. When injectable transient candidates are
 * detected, an object holding transient field details and a reference to the
 * current instance is written into the stream. This object will then be used
 * during deserialization to try to automatically inject values into transient
 * fields of the deserialized object.
 *
 * Object inspection if performed by a pluggable {@link TransientHandler} object
 * whose aim is to provide information about transient fields along with an
 * object identifier for the instance being serialized, in the form of
 * {@link TransientDescriptor} objects.
 *
 * To improve performance, a filter can be provided to inspect only classes
 * known to have injectable transient fields. For example inspection can be
 * restricted to only some packages. Classes from Java Platform are excluded
 * regardless of the configured filters.
 *
 * <pre>
 * {@code
 * new TransientInjectableObjectOutputStream(os, handler,
 *         type -> type.getPackageName().startsWith("com.vaadin.app"))
 *         .writeWithTransients(target);
 * }
 * </pre>
 *
 * Output of this class is meant to be read by
 * {@link TransientInjectableObjectInputStream}.
 *
 * @see TransientInjectableObjectInputStream
 * @see TransientHandler
 * @see TransientDescriptor
 */
public class TransientInjectableObjectOutputStream
        extends SerializationOutputStream {

    static final Pattern INSPECTION_REJECTION_PATTERN = Pattern
            .compile("^(javax?|jakarta|com\\.sun|sun\\.misc)\\..*");
    private static final Predicate<Class<?>> DEFAULT_INSPECTION_FILTER = type -> !INSPECTION_REJECTION_PATTERN
            .matcher(type.getPackageName()).matches();

    private final TransientHandler inspector;
    private final IdentityHashMap<Object, TransientAwareHolder> inspected = new IdentityHashMap<>();
    private final Predicate<Class<?>> injectableFilter;

    private final OutputStream outputStream;
    private final VarHandle depthHandle;
    private final MethodHandle lookupObject;
    private final VarHandle debugStackInfo;
    private final VarHandle debugStackInfoList;
    private final IdentityHashMap<Object, Track> tracking = new IdentityHashMap<>();

    private final boolean trackingEnabled;
    private boolean trackingMode = false;
    private int trackingCounter;

    private TransientInjectableObjectOutputStream(OutputStream out,
            TransientHandler inspector, Predicate<Class<?>> injectableFilter)
            throws IOException {
        super(out);
        Objects.requireNonNull(injectableFilter, "transient inspection filter");
        this.inspector = Objects.requireNonNull(inspector, "transient handler");
        if (injectableFilter != DEFAULT_INSPECTION_FILTER) {
            injectableFilter = DEFAULT_INSPECTION_FILTER.and(injectableFilter);
        }
        this.injectableFilter = injectableFilter;
        this.outputStream = out;
        enableReplaceObject(true);
        if (this.inspector instanceof DebugMode
                && DebugMode.isTrackingAvailable()) {
            depthHandle = tryGetDepthHandle();
            lookupObject = tryGetLookupObject();
            debugStackInfo = tryGetDebugStackHandle();
            debugStackInfoList = tryGetDebugStackListHandle();
            trackingEnabled = true;
        } else {
            depthHandle = null;
            debugStackInfo = null;
            debugStackInfoList = null;
            lookupObject = null;
            trackingEnabled = false;
        }
    }

    public static TransientInjectableObjectOutputStream newInstance(
            OutputStream out, TransientHandler inspector) throws IOException {
        return newInstance(out, inspector, type -> true);
    }

    public static TransientInjectableObjectOutputStream newInstance(
            OutputStream out, TransientHandler inspector,
            Predicate<Class<?>> injectableFilter) throws IOException {
        if (inspector instanceof DebugMode && DebugMode.isTrackingAvailable()) {
            // Debug mode: setup object tracking
            return new TransientInjectableObjectOutputStream(
                    new InternalOutputStream(out), inspector, injectableFilter);
        }
        return new TransientInjectableObjectOutputStream(out, inspector,
                injectableFilter);
    }

    /**
     * OutputStream wrapper that copies object tracking metadata at the top of
     * the stream, so during deserialization it will be possible to access to
     * debug information (object track id, object graph trace, ...) beforehand.
     *
     * This allows for example to print the object graph trace even if the
     * deserialization process fails before reading the whole stream.
     */
    private static class InternalOutputStream extends ByteArrayOutputStream {
        private final OutputStream wrapped;
        private int metadataPosition = -1;

        private InternalOutputStream(OutputStream wrapped) {
            this.wrapped = wrapped;
        }

        /**
         * Marks the position where metadata will be written.
         */
        void markMetadata() {
            metadataPosition = count;
        }

        void copy() throws IOException {
            wrapped.write(Arrays.copyOfRange(buf, metadataPosition + 3, count));
            count = metadataPosition; // prevents copy the metadata again at the
                                      // end of the stream
            writeTo(wrapped);
            count = 0;
            buf = new byte[0];
        }
    }

    @Override
    public void writeWithTransients(Object object) throws IOException {
        inspected.clear();
        tracking.clear();
        if (inspector instanceof DebugMode) {
            ((DebugMode) inspector).onSerializationStart();
        }
        try {
            reset();
            // marks if the stream will contain tracking data
            writeObject(trackingEnabled);
            trackingMode = true;
            writeObject(object);
            trackingMode = false;
            // Append transient fields metadata
            writeObject(new ArrayList<>(inspected.values().stream()
                    .filter(Objects::nonNull).collect(Collectors.toList())));
            flush();
            writeTrackingMetadata();
        } finally {
            inspected.clear();
            tracking.clear();
            trackingMode = false;
        }
    }

    /**
     * Adds tracking information to the stream.
     *
     * For every serialized object, following tracking metadata is written:
     * <ul>
     * <li>tracking id</li>
     * <li>object graph depth</li>
     * <li>object handle</li>
     * </ul>
     *
     * @throws IOException
     *             Any exception thrown by the underlying OutputStream.
     */
    private void writeTrackingMetadata() throws IOException {
        if (outputStream instanceof InternalOutputStream && trackingEnabled) {
            InternalOutputStream cast = (InternalOutputStream) outputStream;
            List<Track> trackList = tracking.values().stream()
                    .filter(Objects::nonNull)
                    .map(t -> t.assignHandle(this::lookupObjectHandle))
                    .filter(t -> t.getHandle() != -1)
                    .collect(Collectors.toList());
            cast.markMetadata();
            reset();
            writeStreamHeader();
            writeObject(true); // debug flag
            writeObject(new ArrayList<>(trackList));
            cast.copy();
        }
    }

    @Override
    protected void writeClassDescriptor(ObjectStreamClass desc)
            throws IOException {
        super.writeClassDescriptor(desc);
        trackClass(desc);
        trackObject(desc);
    }

    @Override
    protected Object replaceObject(Object obj) {
        obj = trackObject(obj);
        // Only application classes might need to be replaced
        if (trackingMode && obj != null) {
            Class<?> type = obj.getClass();
            if (injectableFilter.test(type) && !inspected.containsKey(obj)) {
                Object original = obj;
                TransientAwareHolder holder;
                if (!(obj instanceof Serializable)
                        && inspector instanceof DebugMode) {
                    obj = handleNotSerializable(obj);
                }
                if (obj != null) {
                    List<TransientDescriptor> descriptors = inspector
                            .inspect(obj);
                    if (descriptors.isEmpty()) {
                        getLogger().trace(
                                "No injectable transient fields found for instance of class {}",
                                obj.getClass());
                        holder = null;
                    } else {
                        getLogger().trace(
                                "Found injectable transient fields for instance of class {} : {}",
                                obj.getClass(), descriptors);
                        holder = new TransientAwareHolder(obj, descriptors);
                    }
                } else {
                    getLogger().debug(
                            "Object of type {} will be replaced with NULL and ignored",
                            original.getClass());
                    holder = TransientAwareHolder.NULL;
                }
                // Marks current object as already seen to avoid infinite loops
                // when it will be serialized as part of TransientAwareHolder
                inspected.put(original, holder);
            }
        }
        return obj;
    }

    /**
     * In debug mode notify handler of not Serializable Object and potentially
     * replace current object with a serializable instance or with
     * {@literal null}, to prevent NotSerializableException and continue the
     * inspection on other objects
     */
    protected Object handleNotSerializable(Object obj) {
        DebugMode debugMode = (DebugMode) inspector;
        Object replace = debugMode.onNotSerializableFound(obj)
                .map(Object.class::cast).orElse(obj);
        if (replace == DebugMode.NULLIFY) {
            getLogger().debug(
                    "Unserializable object of type {} replaced with null",
                    obj.getClass());
            return null;
        }
        return replace;
    }

    private Object trackObject(Object obj) {
        if (trackingMode && trackingEnabled && !tracking.containsKey(obj)) {
            if (getLogger().isTraceEnabled()) {
                getLogger().trace("Serializing object {}", obj.getClass());
            }
            Object original = obj;
            try {
                Track track = createTrackObject(++trackingCounter, obj);
                tracking.put(obj, track);
                obj = ((DebugMode) inspector).onSerialize(obj, track);
                if (obj == null) {
                    getLogger().debug(
                            "Object of type {} will be replaced with NULL and ignored",
                            original.getClass());
                } else if (obj != original) {
                    getLogger().debug(
                            "Object of type {} will be replaced by an object of type {}",
                            original.getClass(), obj.getClass());
                }
            } catch (Exception ex) {
                // Ignore. Debug mode handler is not supposed to throw exception
                getLogger().error("Error tracking object of type {}",
                        original.getClass(), ex);
            }
        }
        return obj;
    }

    private int lookupObjectHandle(Object obj) {
        if (lookupObject != null) {
            try {
                return (int) lookupObject.invoke(obj);
            } catch (Throwable ex) {
                // Ignore
                getLogger().trace("Cannot lookup object", ex);
            }
        }
        return -1;
    }

    private static VarHandle tryGetDepthHandle() {
        try {
            return MethodHandles
                    .privateLookupIn(ObjectOutputStream.class,
                            MethodHandles.lookup())
                    .findVarHandle(ObjectOutputStream.class, "depth",
                            int.class);
        } catch (Exception ex) {
            getLogger().trace("Cannot access ObjectOutputStream.depth field",
                    ex);
            return null;
        }
    }

    private MethodHandle tryGetLookupObject() {
        try {
            VarHandle handles = tryGetHandle("handles",
                    Class.forName("java.io.ObjectOutputStream$HandleTable"));
            if (handles != null) {
                return MethodHandles
                        .privateLookupIn(ObjectOutputStream.class,
                                MethodHandles.lookup())
                        .findVirtual(handles.varType(), "lookup",
                                MethodType.methodType(int.class, Object.class))
                        .bindTo(handles.get(this));
            }
        } catch (Exception ex) {
            getLogger().trace(
                    "Cannot access ObjectOutputStream.handles.lookupObject method",
                    ex);
        }
        return null;
    }

    private static VarHandle tryGetHandle(String name, Class<?> type) {
        try {
            return MethodHandles
                    .privateLookupIn(ObjectOutputStream.class,
                            MethodHandles.lookup())
                    .findVarHandle(ObjectOutputStream.class, name, type);
        } catch (Exception ex) {
            getLogger().trace("Cannot access ObjectOutputStream.{} field", name,
                    ex);
            return null;
        }
    }

    private static VarHandle tryGetDebugStackHandle() {
        try {
            return MethodHandles
                    .privateLookupIn(ObjectOutputStream.class,
                            MethodHandles.lookup())
                    .findVarHandle(ObjectOutputStream.class, "debugInfoStack",
                            Class.forName(
                                    "java.io.ObjectOutputStream$DebugTraceInfoStack"));
        } catch (Exception ex) {
            getLogger().trace(
                    "Cannot access ObjectOutputStream.debugInfoStack field.",
                    ex);
            return null;
        }
    }

    private static VarHandle tryGetDebugStackListHandle() {
        try {
            Class<?> debugTraceInfoStackClass = Class
                    .forName("java.io.ObjectOutputStream$DebugTraceInfoStack");
            return MethodHandles
                    .privateLookupIn(debugTraceInfoStackClass,
                            MethodHandles.lookup())
                    .findVarHandle(debugTraceInfoStackClass, "stack",
                            List.class);
        } catch (Exception ex) {
            getLogger().trace(
                    "Cannot access ObjectOutputStream.DebugTraceInfoStack.stack field.",
                    ex);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Track createTrackObject(int id, Object obj) {
        int depth = -1;
        if (depthHandle != null) {
            depth = (int) depthHandle.get(this);
        }
        List<String> stackInfo = null;
        if (debugStackInfo != null) {
            Object stackElement = debugStackInfo.get(this);
            if (stackElement != null && debugStackInfoList != null) {
                stackInfo = new ArrayList<>(
                        (List<String>) debugStackInfoList.get(stackElement));
                Collections.reverse(stackInfo);
            }
        }
        return new Track(id, depth, stackInfo, obj);
    }

    private void trackClass(ObjectStreamClass type) {
        if (trackingMode && inspector instanceof DebugMode
                && getLogger().isTraceEnabled()) {
            String fields = Stream.of(type.getFields())
                    .filter(field -> !field.isPrimitive() && !Serializable.class
                            .isAssignableFrom(field.getType()))
                    .map(field -> String.format("%s %s",
                            field.getType().getName(), field.getName()))
                    .collect(Collectors.joining(", "));
            getLogger().trace(
                    "Inspecting fields of class {} for serialization: [{}]",
                    type.getName(), fields);
        }
    }

    private static Logger getLogger() {
        return LoggerFactory
                .getLogger(TransientInjectableObjectOutputStream.class);
    }

}
