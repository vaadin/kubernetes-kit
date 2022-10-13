package com.vaadin.azure.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.session.SaveMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

/**
 * This configuration bean is provided to auto-configure Vaadin apps to run in a
 * clustered environment.
 */
@AutoConfiguration
public class AzureKitConfiguration {

    /**
     * Provides a {@link SessionRepositoryCustomizer} bean to configure
     * {@link RedisIndexedSessionRepository} to work with Vaadin sessions.
     *
     * @return the session-repository customizer
     */
    @Bean("vaadinAzureKitRedisSessionRepositoryCustomizer")
    @ConditionalOnClass(RedisIndexedSessionRepository.class)
    public SessionRepositoryCustomizer<RedisIndexedSessionRepository> redisSessionRepositoryCustomizer() {
        return repository -> {
            repository.setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
        };
    }
}
