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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.DebugMode;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.Track;

/**
 * An {@link ObjectInputStream} implementation that takes care to inject known
 * transient fields during deserialization.
 *
 * It expects a stream written by {@link TransientInjectableObjectOutputStream},
 * containing wrapper objects with details usable to inject transient fields on
 * deserialized object.
 *
 * Injection is performed by a pluggable {@link TransientHandler} component,
 * taking as input a deserialized object and transient fields information in the
 * form of {@link TransientDescriptor} objects.
 *
 * @see TransientInjectableObjectOutputStream#replaceObject(Object)
 * @see TransientHandler
 * @see TransientDescriptor
 */
public class TransientInjectableObjectInputStream extends SerializationInputStream {

    private final VarHandle passHandleHandle;
    private final MethodHandle handlesLookupObjectHandle;
    private final MethodHandle handlesSizeObjectHandle;
    private final TransientHandler injector;

    private Map<Integer, Track> tracked;

    public TransientInjectableObjectInputStream(InputStream in,
            TransientHandler injector) throws IOException {
        super(in);
        this.injector = injector;
        if (injector instanceof DebugMode && DebugMode.isTrackingAvailable()) {
            passHandleHandle = tryGetHandle("passHandle", int.class);
            handlesLookupObjectHandle = tryGetHandlesLookupObject();
            handlesSizeObjectHandle = tryGetHandlesSize();
        } else {
            passHandleHandle = null;
            handlesLookupObjectHandle = null;
            handlesSizeObjectHandle = null;
        }
        enableResolveObject(true);
    }

    @Override
    protected void readStreamHeader() throws IOException {
        setObjectInputFilter(new TrackingFilter());
        super.readStreamHeader();
        try {
            boolean hasTrackingData = (boolean) readObject();
            if (hasTrackingData) {
                List<Track> trackList = (List<Track>) readObject();
                tracked = trackList.stream().filter(t -> t.getHandle() != -1)
                        .collect(Collectors.toMap(Track::getHandle,
                                Function.identity()));
                super.readStreamHeader();
                readObject(); // Debug mode flag is duplicated on tracked stream
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    private final class TrackingFilter implements ObjectInputFilter {

        @Override
        public Status checkInput(FilterInfo filterInfo) {
            if (TransientInjectableObjectInputStream.this.injector instanceof DebugMode) {
                Track track = TransientInjectableObjectInputStream.this
                        .lookupCurrentTrackedObject();
                Object currentObject = TransientInjectableObjectInputStream.this
                        .lookupCurrentObject();
                Class<?> serialClass = filterInfo.serialClass();
                if (serialClass != null || track != null) {

                    // Track classes being deserialized for debugging purpose
                    if (track != null && track.depth == -1) {
                        // gather data with reflection not enabled
                        // use FilterInfo
                        track = track
                                .withEstimatedDepth((int) filterInfo.depth());
                    } else if (track == null) {
                        track = Track.unknown((int) filterInfo.depth(),
                                serialClass);
                    }
                    if (serialClass != null && currentObject == null) {
                        // First time the class is inspected
                        // next time the handle will be read and
                        // currentObject will be a ObjectStreamClass instance
                        currentObject = ObjectStreamClass.lookup(serialClass);
                        if (track.id == -1) {
                            track = track
                                    .withEstimatedHandle(estimateNextHandle());
                        }
                    }
                    try {
                        ((DebugMode) TransientInjectableObjectInputStream.this.injector)
                                .onDeserialize(serialClass, track,
                                        currentObject);
                    } catch (Exception ex) {
                        // Ignore, debug handler is not supposed to throw
                        // exception
                        // that may stop deserialization process
                    }
                }
            }
            return Status.UNDECIDED;
        }

    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc)
            throws IOException, ClassNotFoundException {
        try {
            // Uses Thread context class loader to load class to avoid
            // mismatches with framework that may use classloaders different
            // from the application one (e.g. Spring RestartClassLoader)
            return Class.forName(desc.getName(), false,
                    Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException ex) {
            return super.resolveClass(desc);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T readWithTransients()
            throws IOException, ClassNotFoundException {
        if (injector instanceof DebugMode) {
            ((DebugMode) injector).onDeserializationStart();
        }
        Object out = readObject();
        // Read TransientAwareHolder to inject transient fields
        List<TransientAwareHolder> holders = (List<TransientAwareHolder>) readObject();
        holders.forEach(this::injectTransients);
        return (T) out;
    }

    @Override
    protected Object resolveObject(Object obj) {
        if (injector instanceof DebugMode) {
            // track deserialized objects for debugging purpose
            try {
                Track track = TransientInjectableObjectInputStream.this
                        .lookupCurrentTrackedObject();
                obj = ((DebugMode) injector).onDeserialized(obj, track);
            } catch (Exception ex) {
                // Ignore, debug handler is not supposed to throw exception
                // that may stop deserialization process
            }
        }
        return obj;
    }

    private void injectTransients(TransientAwareHolder holder) {
        Object obj = holder.source();
        if (obj != null) {
            List<TransientDescriptor> descriptors = holder.transients();
            getLogger().debug(
                    "Extract injectable instance of type {} from holder object with transient descriptors: {}",
                    obj.getClass(), descriptors);
            getLogger().debug("Try injection into {}", obj.getClass());
            try {
                holder.inVaadinScope(() -> injector.inject(obj, descriptors));
            } catch (Exception ex) {
                getLogger().error(
                        "Failed to inject transient fields into type {}",
                        obj.getClass(), ex);
            }
        } else {
            getLogger().trace("Ignoring NULL TransientAwareHolder");
        }
    }

    private static Logger getLogger() {
        return LoggerFactory
                .getLogger(TransientInjectableObjectInputStream.class);
    }

    private Object lookupCurrentObject() {
        if (passHandleHandle != null) {
            return lookupObject((int) passHandleHandle.get(this));
        }
        return null;
    }

    private Object lookupObject(int handle) {
        if (handlesLookupObjectHandle != null) {
            try {
                return handlesLookupObjectHandle.invoke(handle);
            } catch (Throwable ex) {
                getLogger().trace("Cannot lookup object", ex);
            }
        }
        return null;
    }

    private int estimateNextHandle() {
        if (handlesSizeObjectHandle != null) {
            try {
                return (int) handlesSizeObjectHandle.invoke();
            } catch (Throwable ex) {
                getLogger().trace("Cannot guess handle by reading current size",
                        ex);
            }
        }
        return -1;
    }

    private Track lookupCurrentTrackedObject() {
        if (passHandleHandle != null) {
            return lookupTrackedObject((int) passHandleHandle.get(this));
        }
        return null;
    }

    private Track lookupTrackedObject(int handle) {
        return tracked.get(handle);
    }

    private static VarHandle tryGetHandle(String name, Class<?> type) {
        try {
            return MethodHandles
                    .privateLookupIn(ObjectInputStream.class,
                            MethodHandles.lookup())
                    .findVarHandle(ObjectInputStream.class, name, type);
        } catch (Exception ex) {
            getLogger().trace("Cannot access ObjectInputStream.{} field", name,
                    ex);
            return null;
        }
    }

    private MethodHandle tryGetHandlesLookupObject() {
        try {
            VarHandle handles = tryGetHandle("handles",
                    Class.forName("java.io.ObjectInputStream$HandleTable"));
            if (handles != null) {
                return MethodHandles
                        .privateLookupIn(ObjectInputStream.class,
                                MethodHandles.lookup())
                        .findVirtual(handles.varType(), "lookupObject",
                                MethodType.methodType(Object.class, int.class))
                        .bindTo(handles.get(this));
            }
        } catch (Exception ex) {
            getLogger().trace(
                    "Cannot access ObjectOutputStream.handles.lookupObject method",
                    ex);
        }
        return null;
    }

    private MethodHandle tryGetHandlesSize() {
        try {
            VarHandle handles = tryGetHandle("handles",
                    Class.forName("java.io.ObjectInputStream$HandleTable"));
            if (handles != null) {
                return MethodHandles
                        .privateLookupIn(ObjectInputStream.class,
                                MethodHandles.lookup())
                        .findVirtual(handles.varType(), "size",
                                MethodType.methodType(int.class))
                        .bindTo(handles.get(this));
            }
        } catch (Exception ex) {
            getLogger().trace(
                    "Cannot access ObjectOutputStream.handles.lookupObject method",
                    ex);
        }
        return null;
    }

    public static Object onDebugMode(ObjectInputStream is,
            Function<DebugMode, Object> action) {
        if (is instanceof TransientInjectableObjectInputStream
                && ((TransientInjectableObjectInputStream) is).injector instanceof DebugMode) {
            DebugMode debugMode = (DebugMode) ((TransientInjectableObjectInputStream) is).injector;
            return action.apply(debugMode);
        } else {
            getLogger().trace("Cannot get a DebugMode for {}", is.getClass());
        }
        return null;
    }

}
