package com.vaadin.kubernetes.starter;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.vaadin.pro.licensechecker.LicenseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LicenseCheckConditionTest {

    private static final String VERSION = "1.0";

    private static final String EXCEPTION_MESSAGE = "Programmatically fail";

    @Test
    public void validLicense_positiveConditionOutcome() {
        var condition = new TestableLicenseCheckCondition(true);
        var outcome = condition.getMatchOutcome(null, null);

        assertTrue(outcome.isMatch());
    }

    @Test
    public void invalidLicense_negativeConditionOutcome() {
        var condition = new TestableLicenseCheckCondition(false);
        var outcome = condition.getMatchOutcome(null, null);

        assertFalse(outcome.isMatch());
        assertEquals(EXCEPTION_MESSAGE, outcome.getMessage());
    }

    static class TestableLicenseCheckCondition extends LicenseCheckCondition {

        private final boolean shouldSucceed;

        private TestableLicenseCheckCondition(boolean shouldSucceed) {
            this.shouldSucceed = shouldSucceed;
        }

        @Override
        Properties getProperties() {
            var properties = new Properties();
            properties.setProperty(LicenseCheckCondition.VERSION_PROPERTY,
                    VERSION);
            return properties;
        }

        @Override
        void checkLicense(String version) {
            if (!shouldSucceed) {
                throw new LicenseException(EXCEPTION_MESSAGE);
            }
        }
    }
}
