package com.vaadin.azure.starter.sessiontracker.serialization;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * restricted to only some packages.
 * 
 * <pre>
 * {@code 
 * new TransientInjectableObjectOutputStream(
 *      os, handler, type -> type.getPackageName().startsWith("com.vaadin.app")
 *  ).writeWithTransients(target);
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
public class TransientInjectableObjectOutputStream extends ObjectOutputStream {

    private final TransientHandler inspector;
    private final IdentityHashMap<Object, TransientAwareHolder> seen = new IdentityHashMap<>();
    private final Predicate<Class<?>> injectableFilter;

    public TransientInjectableObjectOutputStream(OutputStream out,
            TransientHandler inspector) throws IOException {
        this(out, inspector, type -> true);
    }

    public TransientInjectableObjectOutputStream(OutputStream out,
            TransientHandler inspector, Predicate<Class<?>> injectableFilter)
            throws IOException {
        super(out);
        this.inspector = inspector;
        this.injectableFilter = injectableFilter;
        enableReplaceObject(true);
    }

    public void writeWithTransients(Object object) throws IOException {
        seen.clear();
        try {
            writeObject(object);
            // Append transient fields metadata
            writeObject(new ArrayList<>(seen.values().stream()
                    .filter(Objects::nonNull).collect(Collectors.toList())));
        } finally {
            seen.clear();
        }
    }

    @Override
    protected Object replaceObject(Object obj) {
        track(obj);
        Class<?> type = obj.getClass();
        if (injectableFilter.test(type) && !seen.containsKey(obj)) {
            TransientAwareHolder holder;
            Object original = obj;
            obj = handleNotSerializable(obj);
            if (obj != null) {
                List<TransientDescriptor> descriptors = inspector.inspect(obj);
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
            seen.put(original, holder);
        }
        return obj;
    }

    /**
     * In debug mode notify handler of not Serializable Object and potentially
     * replace current object with a serializable instance or with
     * {@literal null}, to prevent NotSerializableException and continue the
     * inspection on other objects
     */
    private Object handleNotSerializable(Object obj) {
        Object replacement = obj;
        if (!(obj instanceof Serializable)
                && inspector instanceof TransientHandler.DebugMode) {
            TransientHandler.DebugMode debugMode = (TransientHandler.DebugMode) inspector;
            replacement = debugMode.onNotSerializableFound(obj)
                    .map(Object.class::cast).orElse(obj);
            if (replacement == TransientHandler.DebugMode.NULLIFY) {
                return null;
            }
        }
        return replacement;
    }

    private void track(Object obj) {
        if (inspector instanceof TransientHandler.DebugMode) {
            try {
                ((TransientHandler.DebugMode) inspector).onSerialize(obj);
            } catch (Exception ex) {
                // Ignore
            }
        }
    }

    private static Logger getLogger() {
        return LoggerFactory
                .getLogger(TransientInjectableObjectInputStream.class);
    }

}
