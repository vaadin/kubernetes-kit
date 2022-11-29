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
import java.io.Serializable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientDescriptor;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientHandler;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientInjectableObjectInputStream;

class DebugTransientHandler implements TransientHandler, DebugMode {

    private final Job job;

    private final IdentityHashMap<Object, Unserializable> unserializableReplacements = new IdentityHashMap<>();

    DebugTransientHandler(Job job) {
        this.job = job;
    }

    @Override
    public void inject(Object object, List<TransientDescriptor> transients) {
        // NO-OP
    }

    @Override
    public List<TransientDescriptor> inspect(Object object) {
        return Collections.emptyList();
    }

    @Override
    public Optional<Serializable> onNotSerializableFound(Object object) {
        job.notSerializable(object);
        Serializable replacement = unserializableReplacements.get(object);
        if (replacement == null) {
            replacement = NULLIFY;
        }
        return Optional.of(replacement);
    }

    @Override
    public Object onSerialize(Object object, Track track) {
        if (!(object instanceof Serializable)) {
            unserializableReplacements.put(object,
                    new Unserializable(track.id, object));
        } else {
            object = FailingToStringReplacer
                    .wrapIfNeeded((Serializable) object);
        }
        job.track(object, track);
        return object;
    }

    @Override
    public void onDeserialize(Class<?> type, Track track, Object object) {
        job.pushDeserialization(track, object);
    }

    @Override
    public Object onDeserialized(Object object, Track track) {
        job.popDeserialization(track, object);
        if (object instanceof Unserializable && track != null) {
            return resolveUnserializable(track.id);
        }
        return object;
    }

    private Object resolveUnserializable(int trackId) {
        return unserializableReplacements.entrySet().stream()
                .filter(e -> e.getValue().trackId == trackId).findFirst()
                .map(Map.Entry::getKey).orElse(null);
    }

    private static Object tryResolveUnserializable(
            ObjectInputStream inputStream, Unserializable unserializable) {
        return TransientInjectableObjectInputStream.onDebugMode(inputStream,
                debugMode -> {
                    if (debugMode instanceof DebugTransientHandler) {
                        return ((DebugTransientHandler) debugMode)
                                .resolveUnserializable(unserializable.trackId);
                    }
                    return null;
                });
    }

    /**
     * Replacement for an unserializable object.
     *
     * It will be serialized as {@literal null}, but on deserialization resolves
     * as the original object.
     */
    private static class Unserializable implements Serializable {
        private final int trackId;
        private transient Object replaced;

        public Unserializable(int trackId, Object replaced) {
            this.trackId = trackId;
            this.replaced = replaced;
        }

        private void readObject(ObjectInputStream is)
                throws IOException, ClassNotFoundException {
            is.defaultReadObject();
            replaced = tryResolveUnserializable(is, this);
        }

        private Object readResolve() {
            return replaced;
        }
    }
}
