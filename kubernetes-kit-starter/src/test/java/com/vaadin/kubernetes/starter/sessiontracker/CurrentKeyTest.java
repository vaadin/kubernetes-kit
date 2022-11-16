package com.vaadin.kubernetes.starter.sessiontracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CurrentKeyTest {
    @AfterEach
    void cleanUp() {
        CurrentKey.clear();
    }

    @Test
    void set_keyIsSet() {
        CurrentKey.set(CurrentKey.COOKIE_NAME);

        assertEquals(CurrentKey.COOKIE_NAME, CurrentKey.get());
    }

    @Test
    void set_clear_keyIsNull() {
        CurrentKey.set(CurrentKey.COOKIE_NAME);
        CurrentKey.clear();

        assertNull(CurrentKey.get());
    }
}
