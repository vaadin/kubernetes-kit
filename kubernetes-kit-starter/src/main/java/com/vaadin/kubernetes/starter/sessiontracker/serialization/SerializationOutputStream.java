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
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public abstract class SerializationOutputStream extends ObjectOutputStream {
    public SerializationOutputStream(OutputStream out) throws IOException {
        super(out);
    }

    public abstract void writeWithTransients(Object object) throws IOException;
}
