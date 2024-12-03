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
import java.io.ObjectInputStream;

public abstract class SerializationInputStream extends ObjectInputStream {
    public SerializationInputStream(InputStream in) throws IOException {
        super(in);
    }

    public abstract <T> T readWithTransients() throws IOException, ClassNotFoundException;
}
