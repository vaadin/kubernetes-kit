package com.vaadin.azure.starter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.session.SaveMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.hazelcast.Hazelcast4IndexedSessionRepository;

import com.vaadin.azure.starter.AzureKitConfiguration.HazelcastSessionRepositoryConfiguration;
import com.vaadin.azure.starter.AzureKitConfiguration.RedisSessionRepositoryConfiguration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AzureKitConfigurationTest {

    private WebApplicationContextRunner contextRunner;

    @BeforeEach
    public void init() {
        contextRunner = new WebApplicationContextRunner();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void redisRepositoryAvailable_saveModeConfigured() {
        contextRunner
                .withConfiguration(AutoConfigurations
                        .of(RedisSessionRepositoryConfiguration.class))
                .run(ctx -> {
                    final var customizer = ctx.getBean(
                            "vaadinAzureKitRedisSessionRepositoryCustomizer",
                            SessionRepositoryCustomizer.class);
                    final var mockRepository = mock(
                            RedisIndexedSessionRepository.class);
                    customizer.customize(mockRepository);
                    verify(mockRepository)
                            .setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    public void hazelcastRepositoryAvailable_saveModeConfigured() {
        contextRunner
                .withConfiguration(AutoConfigurations
                        .of(HazelcastSessionRepositoryConfiguration.class))
                .run(ctx -> {
                    final var customizer = ctx.getBean(
                            "vaadinAzureKitHazelcastSessionRepositoryCustomizer",
                            SessionRepositoryCustomizer.class);
                    final var mockRepository = mock(
                            Hazelcast4IndexedSessionRepository.class);
                    customizer.customize(mockRepository);
                    verify(mockRepository)
                            .setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
                });
    }
}
