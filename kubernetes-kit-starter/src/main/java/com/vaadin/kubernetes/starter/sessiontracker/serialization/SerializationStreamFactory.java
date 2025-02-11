/*-
 * Copyright (C) 2024 Vaadin Ltd
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
import java.io.OutputStream;
import java.util.function.Predicate;

/**
 * Factory that is used to create new input / output streams for session
 * (de-)serialization.
 *
 * @see TransientInjectableObjectStreamFactory
 **/
public interface SerializationStreamFactory {

    SerializationOutputStream createOutputStream(OutputStream baseOutputStream,
            TransientHandler transientHandler,
            Predicate<Class<?>> injectableFilter) throws IOException;

    SerializationInputStream createInputStream(InputStream in,
            TransientHandler transientHandler) throws IOException;
}
