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

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.testbench.unit.internal.MockVaadin;
import com.vaadin.testbench.unit.mocks.MockService;
import com.vaadin.testbench.unit.mocks.MockVaadinSession;
import com.vaadin.testbench.unit.mocks.MockedUI;

public class TransientAwareHolderTest {

    VaadinSession session;
    UI ui;
    Field field;

    @BeforeEach
    void setUp() throws NoSuchFieldException {
        MockVaadin.setup();
        session = VaadinSession.getCurrent();
        ui = new MockedUI();
        ui.getInternals().setSession(session);
        MockVaadin.tearDown();
        CurrentInstance.clearAll();

        field = Dummy.class.getDeclaredField("myField");
    }

    @AfterEach
    void tearDown() {
        CurrentInstance.clearAll();
    }

    @Test
    void inVaadinScope_vaadinScoped_uiAvailable_threadLocalsSet() {
        UI.setCurrent(ui);
        TransientAwareHolder holder = new TransientAwareHolder(new Object(),
                List.of(new TransientDescriptor(field, "REF", true)));
        CurrentInstance.clearAll();

        ThreadLocalsGrabber grabber = new ThreadLocalsGrabber();
        holder.inVaadinScope(grabber);

        Assertions.assertNotNull(grabber.session,
                "Expected VaadinSession thread local to be set, but was not");
        Assertions.assertNotNull(grabber.ui,
                "Expected UI thread local to be set, but was not");
    }

    @Test
    void inVaadinScope_vaadinScoped_onlySession_threadLocalSet() {
        VaadinSession.setCurrent(session);
        TransientAwareHolder holder = new TransientAwareHolder(new Object(),
                List.of(new TransientDescriptor(field, "REF", true)));
        CurrentInstance.clearAll();

        ThreadLocalsGrabber grabber = new ThreadLocalsGrabber();
        holder.inVaadinScope(grabber);

        Assertions.assertNotNull(grabber.session,
                "Expected VaadinSession thread local to be set, but was not");
        Assertions.assertNull(grabber.ui,
                "Expected UI thread local not to be set, but it was");
    }

    @Test
    void inVaadinScope_notVaadinScoped_threadLocalNotSet() {
        UI.setCurrent(ui);
        TransientAwareHolder holder = new TransientAwareHolder(new Object(),
                List.of(new TransientDescriptor(field, "REF", false)));
        CurrentInstance.clearAll();

        ThreadLocalsGrabber grabber = new ThreadLocalsGrabber();
        holder.inVaadinScope(grabber);

        Assertions.assertNull(grabber.session,
                "Expected VaadinSession thread local not to be set, but it was");
        Assertions.assertNull(grabber.ui,
                "Expected UI thread local not to be set, but it was");
    }

    private static class ThreadLocalsGrabber implements Runnable {

        VaadinSession session;
        UI ui;

        @Override
        public void run() {
            session = VaadinSession.getCurrent();
            ui = UI.getCurrent();
        }
    }

    private static class Dummy {
        private Object myField;
    }

}
