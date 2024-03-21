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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import static com.vaadin.kubernetes.starter.SerializationProperties.PREFIX;

/**
 * Definition of configuration properties for Session serialization.
 *
 * @author Vaadin Ltd
 * @since 1.0
 */
@ConfigurationProperties(prefix = PREFIX)
public class SerializationProperties {

    public static final String PREFIX = "vaadin.serialization";

    public static final int DEFAULT_SERIALIZATION_TIMEOUT_MS = 30000;

    private int timeout = DEFAULT_SERIALIZATION_TIMEOUT_MS;

    @NestedConfigurationProperty
    private final TransientsProperties transients = new TransientsProperties();

    /**
     * Gets the timeout in milliseconds to wait for the serialization to be
     * completed.
     *
     * @return the timeout in milliseconds to wait for the serialization to be
     *         completed, defaults to 30000 ms
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout in milliseconds to wait for the serialization to be
     * completed.
     *
     * @param timeout
     *            the timeout in milliseconds to wait for the serialization to
     *            be completed, defaults to 30000 ms
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * Gets configuration for transient fields handling during serialization.
     *
     * @return configuration for transient fields handling.
     */
    public TransientsProperties getTransients() {
        return transients;
    }

    static class TransientsProperties {
        private final Set<String> includePackages = new HashSet<>();
        private final Set<String> excludePackages = new HashSet<>();

        /**
         * Gets a list of packages to consider during class inspection for
         * injectable transient fields.
         *
         * @return list of packages included in class inspection.
         */
        public Set<String> getIncludePackages() {
            return includePackages;
        }

        /**
         * Gets a list of packages to exclude from class inspection for
         * injectable transient fields.
         *
         * @return list of packages excluded from class inspection.
         */
        public Set<String> getExcludePackages() {
            return excludePackages;
        }

        /**
         * Gets a predicate that filters classes based on include/exclude
         * packages configuration.
         *
         * An empty inclusion list means all classes are included. Exclusion
         * rules have higher priority over inclusion rules.
         *
         * If no inclusion nor exclusion rules are configured all class are
         * eligible for inspection.
         *
         * @return a predicate that filter classes based on configured package
         *         rules.
         */
        public Predicate<Class<?>> transientInjectableFilter() {
            if (includePackages.isEmpty() && excludePackages.isEmpty()) {
                return type -> true;
            }
            return type -> {
                String packageName = type.getPackageName();
                return excludePackages.stream()
                        .noneMatch(packageName::startsWith)
                        && (includePackages.isEmpty() || includePackages
                                .stream().anyMatch(packageName::startsWith));
            };
        }
    }
}
