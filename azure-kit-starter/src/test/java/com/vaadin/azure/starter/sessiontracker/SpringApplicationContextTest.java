package com.vaadin.azure.starter.sessiontracker;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class SpringApplicationContextTest {
    @Test
    void setApplicationContext_contextIsSet() {
        ApplicationContext appContext = mock(ApplicationContext.class);

        SpringApplicationContext springContext = new SpringApplicationContext();
        springContext.setApplicationContext(appContext);

        assertEquals(appContext, SpringApplicationContext.getContext());
    }
}
