package com.vaadin.kubernetes.starter.sessiontracker.serialization.debug;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.vaadin.flow.server.RequestHandler;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import com.vaadin.kubernetes.starter.test.EnableOnJavaIOReflection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnableOnJavaIOReflection
@EnabledIfSystemProperty(named = "sun.io.serialization.extendedDebugInfo", matches = "true", //
        disabledReason = "Tests need system property sun.io.serialization.extendedDebugInfo to be enabled")
class SerializationDebugRequestHandlerInitListenerTest {

    SerializationDebugRequestHandler.InitListener listener = new SerializationDebugRequestHandler.InitListener();

    @Test
    void developmentModeAndSerializationDebugEnabled_requestHandlerInstalled() {
        ServiceInitEvent event = createEvent(false, true);
        listener.serviceInit(event);

        List<RequestHandler> handlers = event.getAddedRequestHandlers()
                .collect(Collectors.toList());
        assertThat(handlers).hasSize(1);
        assertThat(handlers.get(0))
                .isExactlyInstanceOf(SerializationDebugRequestHandler.class);
    }

    @Test
    void developmentModeAndSerializationDebugDisabled_requestHandlerNotInstalled() {
        ServiceInitEvent event = createEvent(false, false);
        listener.serviceInit(event);
        assertThat(event.getAddedRequestHandlers()).isEmpty();
    }

    @Test
    void productionModeAndSerializationDebugEnabled_requestHandlerNotInstalled() {
        ServiceInitEvent event = createEvent(true, false);
        listener.serviceInit(event);
        assertThat(event.getAddedRequestHandlers()).isEmpty();
    }

    @Test
    void productionModeAndSerializationDebugDisabled_requestHandlerNotInstalled() {
        ServiceInitEvent event = createEvent(true, false);
        listener.serviceInit(event);
        assertThat(event.getAddedRequestHandlers()).isEmpty();
    }

    private static ServiceInitEvent createEvent(boolean productionMode,
            boolean serializationDebugEnabled) {
        VaadinService service = mock(VaadinService.class);
        VaadinContext vaadinContext = mock(VaadinContext.class);
        when(service.getContext()).thenReturn(vaadinContext);

        ApplicationConfiguration appConfig = mock(
                ApplicationConfiguration.class);
        when(vaadinContext.getAttribute(eq(ApplicationConfiguration.class),
                any())).thenReturn(appConfig);
        when(appConfig.isProductionMode()).thenReturn(productionMode);
        when(appConfig.isDevModeSessionSerializationEnabled())
                .thenReturn(serializationDebugEnabled);

        return new ServiceInitEvent(service);
    }

}
