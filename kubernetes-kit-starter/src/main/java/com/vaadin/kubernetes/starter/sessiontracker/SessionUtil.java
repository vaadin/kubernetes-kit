/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker;

import java.lang.reflect.Field;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.internal.ReflectTools;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;

/**
 * Utility class for session operations.
 */
public class SessionUtil {

    /**
     * VaadinSession lock is usually set by calling
     * {@link VaadinSession#refreshTransients(WrappedSession, VaadinService)},
     * but during deserialization none of the required objects are available.
     * This method gets the lock instance if exists, or injects a temporary lock
     * instance into the provided {@link VaadinSession} and returns a runnable
     * that will unlock or remove the lock when executed.
     *
     * @param session
     *            the session to be locked if needed
     * @return a runnable that will unlock or remove the lock when executed, or
     *         a no-op in case of any error
     */
    public static Runnable injectLockIfNeeded(VaadinSession session) {
        if (session != null) {
            Lock lock = session.getLockInstance();
            if (lock != null) {
                lock.lock();
                return () -> session.getLockInstance().unlock();
            }
            try {
                Field field = VaadinSession.class.getDeclaredField("lock");
                lock = new ReentrantLock();
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
        return LoggerFactory.getLogger(SessionUtil.class);
    }
}
