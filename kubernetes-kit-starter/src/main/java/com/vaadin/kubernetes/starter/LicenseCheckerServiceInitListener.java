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

import com.vaadin.flow.internal.UsageStatistics;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.pro.licensechecker.BuildType;
import com.vaadin.pro.licensechecker.LicenseChecker;

/**
 * Service initialization listener to verify the license.
 *
 * @author Vaadin Ltd
 */
public class LicenseCheckerServiceInitListener
        implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        final var service = event.getSource();
        final var version = ProductUtils.getVersion();

        UsageStatistics.markAsUsed(ProductUtils.PRODUCT_NAME, version);

        // Check the license at runtime if in development mode
        if (!service.getDeploymentConfiguration().isProductionMode()) {
            // Using a null BuildType to allow trial licensing builds
            // The variable is defined to avoid method signature ambiguity
            BuildType buildType = null;
            LicenseChecker.checkLicense(ProductUtils.PRODUCT_NAME, version,
                    buildType);
        }
    }
}
