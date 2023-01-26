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
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import com.vaadin.flow.internal.UsageStatistics;

import static org.mockito.Mockito.mockStatic;

public class ProductUtilsTest {

    private MockedStatic<UsageStatistics> usageStatistics;

    @BeforeEach
    public void setup() {
        usageStatistics = mockStatic(UsageStatistics.class);
    }

    @AfterEach
    public void cleanup() {
        usageStatistics.close();
    }

    @Test
    public void markFeatureAsUsed_correctFeatureAndVersion() {
        final var version = getVersion();

        ProductUtils.markAsUsed("test");

        usageStatistics.verify(() -> UsageStatistics
                .markAsUsed("vaadin-kubernetes-kit/test", version));
    }

    static String getVersion() {
        return getProperties().getProperty(ProductUtils.VERSION_PROPERTY);
    }

    private static Properties getProperties() {
        try {
            return PropertiesLoaderUtils
                    .loadAllProperties(ProductUtils.PROPERTIES_RESOURCE);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
