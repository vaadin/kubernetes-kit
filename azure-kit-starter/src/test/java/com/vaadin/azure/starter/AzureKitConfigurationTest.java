package com.vaadin.azure.starter;

import org.junit.jupiter.api.Test;
import org.springframework.session.SaveMode;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.hazelcast.Hazelcast4IndexedSessionRepository;

import com.vaadin.azure.starter.AzureKitConfiguration.HazelcastSessionRepositoryConfiguration;
import com.vaadin.azure.starter.AzureKitConfiguration.RedisSessionRepositoryConfiguration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AzureKitConfigurationTest {

    @Test
    public void redisRepositoryAvailable_saveModeConfigured() {
        var configuration = new RedisSessionRepositoryConfiguration();
        var customizer = configuration.redisSessionRepositoryCustomizer();
        var mockRepository = mock(RedisIndexedSessionRepository.class);

        customizer.customize(mockRepository);

        verify(mockRepository).setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
    }

    @Test
    public void hazelcastRepositoryAvailable_saveModeConfigured() {
        var configuration = new HazelcastSessionRepositoryConfiguration();
        var customizer = configuration.hazelcastSessionRepositoryCustomizer();
        var mockRepository = mock(Hazelcast4IndexedSessionRepository.class);

        customizer.customize(mockRepository);

        verify(mockRepository).setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
    }
}
