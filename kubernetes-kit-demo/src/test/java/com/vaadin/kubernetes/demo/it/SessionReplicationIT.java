package com.vaadin.kubernetes.demo.it;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("kubernetes")
@UsePlaywright
class SessionReplicationIT {

    static final String APP_URL = System.getProperty("app.url",
            "http://localhost:8080");

    @Test
    void sessionIsPreservedAfterPodFailover(Page page) throws Exception {
        page.navigate(APP_URL + "/push-counter");

        Locator counter = page.locator(".counter");
        Locator hostname = page.locator(".hostname");
        Locator incrementBtn = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Increment"));

        // Wait for Vaadin to fully bootstrap (constructor calls count(),
        // setting counter to 1). Production mode may take a while.
        assertThat(counter).hasText("1",
                new LocatorAssertions.HasTextOptions().setTimeout(30_000));

        // Click increment a few times to trigger UIDL requests and
        // serialization
        for (int i = 0; i < 3; i++) {
            incrementBtn.click();
        }

        // Wait for the last click to be processed
        assertThat(counter).hasText("4");

        String servingPod = hostname.textContent().trim();

        // Allow time for serialization to Redis after the last UIDL
        // request
        page.waitForTimeout(3000);

        // Delete the pod that is serving our session
        deletePod(servingPod);

        // Vaadin push detects the connection loss and reconnects to the
        // surviving pod. The session is restored from Redis. Wait for
        // the UI to become interactive again (generous timeout to
        // account for pod shutdown, endpoint removal, and
        // reconnection).
        incrementBtn.click(
                new Locator.ClickOptions().setTimeout(120_000));

        // Counter should have continued from 4 (now 5)
        assertThat(counter).hasText("5");

        // Hostname should be different (served by surviving pod)
        String newPod = hostname.textContent().trim();
        assertNotEquals(servingPod, newPod,
                "Request should be served by a different pod");
    }

    static void deletePod(String podName) throws Exception {
        Process process = new ProcessBuilder("kubectl", "delete", "pod",
                podName, "--grace-period=0", "--force")
                        .redirectErrorStream(true).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(
                    process.getInputStream().readAllBytes());
            throw new IOException("kubectl delete pod failed (exit "
                    + exitCode + "): " + output);
        }
    }
}
