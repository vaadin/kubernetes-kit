package com.vaadin.kubernetes.starter.sessiontracker.serialization.debug;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientDescriptor;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientHandler;

class DebugTransientHandler implements TransientHandler, DebugMode {

    private final Job job;

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
        return Optional.of(NULLIFY);
    }

    @Override
    public Object onSerialize(Object object, Track track) {
        job.track(object, track);
        if (object instanceof SerializedLambda) {
            SerializedLambda cast = (SerializedLambda) object;
            String description = String.format(
                    "[%s=%s, %s=%s, %s=%s:%s, " + "%s=%s.%s:%s, %s=%s, %s=%d]",
                    "capturingClass", cast.getCapturingClass(),
                    "functionalInterfaceClass",
                    cast.getFunctionalInterfaceClass(),
                    "functionalInterfaceMethod",
                    cast.getFunctionalInterfaceMethodName(),
                    cast.getFunctionalInterfaceMethodSignature(),
                    "implementation", cast.getImplClass(),
                    cast.getImplMethodName(), cast.getImplMethodSignature(),
                    "instantiatedMethodType", cast.getInstantiatedMethodType(),
                    "numCaptured", cast.getCapturedArgCount());
            job.log(SerializedLambda.class.getName(), description);
        }
        return object;
    }

    @Override
    public void onDeserialize(Class<?> type, Track track, Object object) {
        job.pushDeserialization(type, track, object);
    }

    @Override
    public Object onDeserialized(Object object, Track track) {
        job.popDeserialization(track);
        return object;
    }
}
