package com.vaadin.azure.starter.sessiontracker;

/**
 * Holder for distributed storage session key.
 */
public final class CurrentKey {

    /**
     * Name of the Cookie that carries the distributed storage session key.
     */
    public static final String COOKIE_NAME = "clusterKey";

    private static final ThreadLocal<String> current = new ThreadLocal<>();

    /**
     * Sets the distributed storage session key.
     *
     * @param key
     *            the distributed storage session key
     */
    public static void set(String key) {
        current.set(key);
    }

    /**
     * Clears the current distributed storage session key.
     */
    public static void clear() {
        current.remove();
    }

    /**
     * Gets the current distributed storage session key.
     *
     * @return the current distributed storage session key
     */
    public static String get() {
        return current.get();
    }

}
