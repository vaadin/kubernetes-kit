package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.invoke.MethodHandles;
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
public class TransientInjectableObjectInputStream extends ObjectInputStream {

    private final VarHandle passHandleHandle;
    private final TransientHandler injector;
    private Map<Integer, Track> tracked;

    public TransientInjectableObjectInputStream(InputStream in,
            TransientHandler injector) throws IOException {
        super(in);
        this.injector = injector;
        enableResolveObject(true);
        boolean canAccess = ObjectInputStream.class.getModule().isOpen(
                ObjectInputStream.class.getPackageName(),
                TransientInjectableObjectInputStream.class.getModule());
        if (canAccess) {
            passHandleHandle = tryGetHandle("passHandle", int.class);
        } else {
            getLogger().warn(
                    "Cannot reflect on ObjectInputStream. Please open java.io to UNNAMED module, adding "
                            + "'--add-opens java.base/java.io=ALL-UNNAMED' to the JVM arguments.");
            passHandleHandle = null;
        }
    }

    @Override
    protected void readStreamHeader() throws IOException {
        setObjectInputFilter(new TrackingFilter());
        super.readStreamHeader();
        try {
            boolean hasTrackData = (boolean) readObject();
            if (hasTrackData) {
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
                        .lookupObject();
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
                    try {
                        ((DebugMode) TransientInjectableObjectInputStream.this.injector)
                                .onDeserialize(serialClass, track);
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
    public <T> T readWithTransients()
            throws IOException, ClassNotFoundException {
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
                        .lookupObject();
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
                injector.inject(obj, descriptors);
            } catch (Exception ex) {
                getLogger().error(
                        "Failed to inject transient fields into type {}",
                        obj.getClass());
            }
        } else {
            getLogger().trace("Ignoring NULL TransientAwareHolder");
        }
    }

    private static Logger getLogger() {
        return LoggerFactory
                .getLogger(TransientInjectableObjectInputStream.class);
    }

    private Track lookupObject() {
        try {
            int handle = (int) passHandleHandle.get(this);
            return tracked.get(handle);
        } catch (Throwable e) {
            // Ignore
        }
        return null;
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

}
