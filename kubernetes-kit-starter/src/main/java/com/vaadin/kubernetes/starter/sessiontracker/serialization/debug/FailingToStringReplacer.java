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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * A replacement for object with {@link #toString()} methods throwing
 * exceptions.
 *
 * This class is used when {@literal sun.io.serialization.extendedDebugInfo}
 * system property is set, to prevent serialization process to be stopped by
 * exceptions thrown by {@link #toString()} methods when computing serialization
 * stack.
 *
 * The original object can be replaced by an instance of this class, that during
 * serialization tries to write the fields of the original object (respecting
 * serialization hook methods). On deserialization, it creates new instance of
 * the original objects and fills it with values read from the stream
 * (respecting deserialization hook methods).
 *
 * The class needs reflection to be allowed on {@code java.io} package, so the
 * JVM must be started with the needed add-opens flag.
 *
 * <pre>
 * --add-opens` flag (e.g. '--add-opens java.base/java.io=ALL-UNNAMED')
 * </pre>
 */
class FailingToStringReplacer implements Serializable {
    private transient Serializable target;
    private final Class<?> targetClass;

    FailingToStringReplacer(Serializable target) {
        this.target = target;
        this.targetClass = target.getClass();
    }

    static Object wrapIfNeeded(Serializable target) {
        if (target != null) {
            try {
                target.toString();
            } catch (Exception ex) {
                target = new FailingToStringReplacer(target);
            }
        }
        return target;
    }

    @Override
    public String toString() {
        return "Replacement for " + target.getClass()
                + " because of throwing toString()";
    }

    private void writeObject(ObjectOutputStream os) throws IOException {
        os.defaultWriteObject();
        ObjectStreamClass lookup = ObjectStreamClass.lookup(targetClass);
        writeSerialData(os, target, lookup);
    }

    private void readObject(ObjectInputStream is)
            throws IOException, ClassNotFoundException {
        is.defaultReadObject();
        ObjectStreamClass descr = ObjectStreamClass.lookup(targetClass);
        target = (Serializable) newInstance(descr);
        readSerialData(is, target, descr);
    }

    private Object readResolve() {
        return target;
    }

    private Object newInstance(ObjectStreamClass descr) {
        try {
            return MethodHandles
                    .privateLookupIn(ObjectStreamClass.class,
                            MethodHandles.lookup())
                    .findVirtual(ObjectStreamClass.class, "newInstance",
                            MethodType.methodType(Object.class))
                    .invoke(descr);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private void writeSerialData(ObjectOutputStream oos, Object obj,
            ObjectStreamClass desc) {
        try {
            MethodHandles
                    .privateLookupIn(ObjectOutputStream.class,
                            MethodHandles.lookup())
                    .findVirtual(ObjectOutputStream.class, "writeSerialData",
                            MethodType.methodType(void.class, Object.class,
                                    ObjectStreamClass.class))
                    .bindTo(oos).invoke(obj, desc);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private void readSerialData(ObjectInputStream is, Object obj,
            ObjectStreamClass desc) {
        try {
            MethodHandles
                    .privateLookupIn(ObjectInputStream.class,
                            MethodHandles.lookup())
                    .findVirtual(ObjectInputStream.class, "readSerialData",
                            MethodType.methodType(void.class, Object.class,
                                    ObjectStreamClass.class))
                    .bindTo(is).invoke(obj, desc);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

}
