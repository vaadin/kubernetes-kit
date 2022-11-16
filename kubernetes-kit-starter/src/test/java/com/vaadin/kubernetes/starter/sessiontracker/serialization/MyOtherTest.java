package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.module.ModuleDescriptor;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class MyOtherTest {

    @Test
    void testMe() throws Exception {
        Module javaBase = ModuleLayer.boot().findModule("java.base").orElseThrow();
        Module classModule = getClass().getModule();

        javaBase.isOpen("java.io", classModule);
        Set<ModuleDescriptor.Opens> opens = javaBase.getDescriptor().opens();

        javaBase.addOpens("java.io", classModule);
        VarHandle depth = MethodHandles
                .privateLookupIn(ObjectOutputStream.class,
                        MethodHandles.lookup())
                .findVarHandle(ObjectOutputStream.class, "depth",
                        int.class);
    }

}
