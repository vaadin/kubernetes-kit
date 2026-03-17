/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.vaadin.kubernetes.starter.sessiontracker.backend.RedisConnector;

/**
 * Auto-configuration for Redis-based session backend.
 */
@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnClass(DataRedisAutoConfiguration.class)
public class RedisConfiguration {

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean
    RedisConnector redisConnector(RedisConnectionFactory factory) {
        return new RedisConnector(factory);
    }
}
