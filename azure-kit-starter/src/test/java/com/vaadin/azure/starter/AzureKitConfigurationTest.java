package com.vaadin.azure.starter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.session.SaveMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AzureKitConfigurationTest {

    private WebApplicationContextRunner contextRunner;

    @BeforeEach
    public void init() {
        contextRunner = new WebApplicationContextRunner().withConfiguration(
                AutoConfigurations.of(AzureKitConfiguration.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void resiRepositoryAvailable_saveModeConfigured() {
        contextRunner.run(ctx -> {
            final var customizer = ctx.getBean(
                    "vaadinAzureKitRedisSessionRepositoryCustomizer",
                    SessionRepositoryCustomizer.class);
            final var mockRepository = mock(
                    RedisIndexedSessionRepository.class);
            customizer.customize(mockRepository);
            verify(mockRepository).setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
        });
    }
}
