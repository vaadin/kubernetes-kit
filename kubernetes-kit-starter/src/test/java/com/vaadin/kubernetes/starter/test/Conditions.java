package com.vaadin.kubernetes.starter.test;

import java.io.ObjectOutputStream;

import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.DebugMode;

public class Conditions {

    public static boolean javaIOOpenForReflection() {
        return ObjectOutputStream.class.getModule().isOpen("java.io",
                DebugMode.class.getModule());
    }
}
