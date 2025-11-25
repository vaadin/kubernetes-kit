package com.vaadin.kubernetes.demo;

import com.vaadin.kubernetes.starter.sessiontracker.SessionSerializationCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;

/**
 * The entry point of the Spring Boot application.
 *
 * Use the @PWA annotation make the application installable on phones, tablets
 * and some desktop browsers.
 *
 */
@SpringBootApplication
@Theme("kuberneteskitdemo")
@PWA(name = "Kubernetes Kit Demo", shortName = "Kubernetes Kit Demo")
@NpmPackage(value = "line-awesome", version = "1.3.0")
@NpmPackage(value = "@vaadin-component-factory/vcf-nav", version = "1.0.6")
@Push
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * A {@link SessionSerializationCallback} that simulates slow serialization
     * to troubleshoot serialization/deserialization issues.
     * <p>
     * The bean is activated by the {@code vaadin.test.slow-serializer.enable}
     * property. Serialization and deserialization delays are configurable via
     * the {@code vaadin.test.slow-serializer.serialization-delay} and
     * {@code vaadin.test.slow-serializer.deserialization-delay} properties,
     * expressing the delay in milliseconds.
     *
     * @param serializationDelay
     *            the amount of time to wait before completing the serialization
     *            process.
     * @param deserializationDelay
     *            the amount of time to wait before completing the
     *            deserialization process.
     * @return a {@link SessionSerializationCallback} that simulates slow
     *         serialization/deserialization.
     */
    @ConditionalOnBooleanProperty("vaadin.test.slow-serializer.enable")
    @Bean
    SessionSerializationCallback simulateSlowSerialization(
            @Value("${vaadin.test.slow-serializer.serialization-delay:5000}") long serializationDelay,
            @Value("${vaadin.test.slow-serializer.deserialization-delay:5000}") long deserializationDelay) {

        return new SessionSerializationCallback() {
            @Override
            public void onSerializationSuccess() {
                sleep(serializationDelay);
            }

            @Override
            public void onDeserializationSuccess() {
                sleep(deserializationDelay);
            }

            private void sleep(long millis) {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }
}
