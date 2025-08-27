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

import com.vaadin.flow.server.startup.BaseLicenseCheckerServiceInitListener;

/**
 * Service initialization listener to verify the license.
 *
 * @author Vaadin Ltd
 */
public class LicenseCheckerServiceInitListener
        extends BaseLicenseCheckerServiceInitListener {

    public LicenseCheckerServiceInitListener() {
        super(ProductUtils.PRODUCT_NAME, ProductUtils.getVersion());
    }
}
