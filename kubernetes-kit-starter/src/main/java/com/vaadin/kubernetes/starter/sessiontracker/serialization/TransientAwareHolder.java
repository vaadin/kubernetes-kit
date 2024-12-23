/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.internal.ReflectTools;
import com.vaadin.flow.server.VaadinSession;

/**
 * A serializable class that holds information about an object to be
 * serialized/deserialized and its transient fields that can be injected after
 * deserialization.
 *
 * For internal use only.
 */
final class TransientAwareHolder implements Serializable {

    static final TransientAwareHolder NULL = new TransientAwareHolder(null,
            Collections.emptyList());

    private final List<TransientDescriptor> transientDescriptors;
    private final Object source; // NOSONAR
    private final UI ui;
    private final VaadinSession session;

    TransientAwareHolder(Object source, List<TransientDescriptor> descriptors) {
        this.source = source;
        this.transientDescriptors = new ArrayList<>(descriptors);
        if (descriptors.stream()
                .anyMatch(TransientDescriptor::isVaadinScoped)) {
            this.ui = UI.getCurrent();
            this.session = ui != null ? ui.getSession()
                    : VaadinSession.getCurrent();
        } else {
            this.ui = null;
            this.session = null;
        }
    }

    /**
     * Gets the list of descriptor of transient fields capable to be injected
     * after deserialization.
     *
     * @return list of injectable transient fields descriptors.
     */
    List<TransientDescriptor> transients() {
        return new ArrayList<>(transientDescriptors);
    }

    /**
     * Gets the object to be serialized and deserialized.
     *
     * @return object to be serialized and deserialized.
     */
    Object source() {
        return source;
    }

    /**
     * Executes the given runnable making sure that Vaadin thread locals are
     * set, when they are available.
     * 
     * @param runnable
     *            the action to execute.
     */
    void inVaadinScope(Runnable runnable) {
        Map<Class<?>, CurrentInstance> instanceMap = null;
        if (ui != null) {
            instanceMap = CurrentInstance.setCurrent(ui);
        } else if (session != null) {
            instanceMap = CurrentInstance.setCurrent(session);
        }
        Runnable cleaner = injectLock(session);
        try {
            runnable.run();
        } finally {
            if (instanceMap != null) {
                CurrentInstance.restoreInstances(instanceMap);
                cleaner.run();
            }
        }
    }

    // VaadinSession lock is usually set by calling
    // VaadinSession.refreshTransients(WrappedSession,VaadinService), but during
    // deserialization none of the required objects are available.
    // This method injects a temporary lock instance into the provided
    // VaadinSession and returns a runnable that will remove it when executed.
    private static Runnable injectLock(VaadinSession session) {
        if (session != null) {
            try {
                Field field = VaadinSession.class.getDeclaredField("lock");
                Lock lock = new ReentrantLock();
                lock.lock();
                ReflectTools.setJavaFieldValue(session, field, lock);
                return () -> removeLock(session, field);
            } catch (NoSuchFieldException e) {
                getLogger().debug("Cannot access lock field on VaadinSession",
                        e);
            }
        }
        return () -> {
        };
    }

    private static void removeLock(VaadinSession session, Field field) {
        session.getLockInstance().unlock();
        ReflectTools.setJavaFieldValue(session, field, null);
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(TransientAwareHolder.class);
    }
}
