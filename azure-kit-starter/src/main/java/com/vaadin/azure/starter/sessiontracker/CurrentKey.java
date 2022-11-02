package com.vaadin.azure.starter.sessiontracker;

public class CurrentKey {

    public static final String COOKIE_NAME = "clusterKey";

    private static final ThreadLocal<String> current = new ThreadLocal<>();

    public static void set(String key) {
        current.set(key);
    }

    public static void clear() {
        current.remove();
    }

    public static String get() {
        return current.get();
    }

}
