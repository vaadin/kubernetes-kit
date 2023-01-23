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

import java.io.IOException;

import com.vaadin.flow.internal.UsageStatistics;

import static org.springframework.core.io.support.PropertiesLoaderUtils.loadAllProperties;

public final class ProductUtils {

    static final String PROPERTIES_RESOURCE = "kubernetes-kit.properties";

    static final String VERSION_PROPERTY = "kubernetes-kit.version";

    static final String PRODUCT_NAME = "vaadin-kubernetes-kit";

    public static void markAsUsed(String feature) {
        final var version = getVersion();
        UsageStatistics.markAsUsed(PRODUCT_NAME + '/' + feature, version);
    }

    static String getVersion() {
        try {
            final var properties = loadAllProperties(PROPERTIES_RESOURCE);
            return properties.getProperty(VERSION_PROPERTY);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
