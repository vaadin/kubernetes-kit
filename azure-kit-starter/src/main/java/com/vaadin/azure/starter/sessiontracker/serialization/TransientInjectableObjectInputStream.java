package com.vaadin.azure.starter.sessiontracker.serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link ObjectInputStream} implementation that takes care to inject known
 * transient fields during deserialization.
 *
 * It expects a stream written by {@link TransientInjectableObjectOutputStream},
 * containing wrapper objects with details usable to inject transient fields on
 * deserialized object.
 *
 * Injection is performed by a pluggable {@link TransientInjector} component,
 * taking as input a deserialized object and transient fields information in the
 * form of {@link TransientDescriptor} objects.
 *
 * @see TransientInjectableObjectOutputStream#replaceObject(Object)
 * @see TransientInjector
 * @see TransientDescriptor
 */
public class TransientInjectableObjectInputStream extends ObjectInputStream {

    private final TransientInjector injector;

    public TransientInjectableObjectInputStream(InputStream in,
            TransientInjector injector) throws IOException {
        super(in);
        this.injector = injector;
        enableResolveObject(true);
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

    private void injectTransients(TransientAwareHolder holder) {
        List<TransientDescriptor> descriptors = holder.transients();
        Object obj = holder.source();
        getLogger().debug(
                "Extract injectable instance of type {} from holder object with transient descriptors: {}",
                obj.getClass(), descriptors);
        getLogger().debug("Try injection into {}", obj.getClass());
        try {
            injector.inject(obj, descriptors);
        } catch (Exception ex) {
            getLogger().error("Failed to inject transient fields into type {}",
                    obj.getClass());
        }
    }

    private static Logger getLogger() {
        return LoggerFactory
                .getLogger(TransientInjectableObjectInputStream.class);
    }
}
