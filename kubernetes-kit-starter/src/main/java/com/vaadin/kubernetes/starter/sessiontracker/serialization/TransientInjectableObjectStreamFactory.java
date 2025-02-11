package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Predicate;

/**
 * Factory that is used to create new
 * {@link TransientInjectableObjectOutputStream} and
 * {@link TransientInjectableObjectInputStream} for session (de-)serialization.
 **/
public class TransientInjectableObjectStreamFactory
        implements SerializationStreamFactory {

    @Override
    public SerializationOutputStream createOutputStream(
            OutputStream baseOutputStream, TransientHandler transientHandler,
            Predicate<Class<?>> injectableFilter) throws IOException {
        return TransientInjectableObjectOutputStream.newInstance(
                baseOutputStream, transientHandler, injectableFilter);
    }

    @Override
    public SerializationInputStream createInputStream(InputStream in,
            TransientHandler transientHandler) throws IOException {
        return new TransientInjectableObjectInputStream(in, transientHandler);
    }
}
