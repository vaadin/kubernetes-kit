package com.vaadin.azure.starter;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.vaadin.azure.starter.sessiontracker.serialization.SerializationDebugRequestHandler;

class VaadinReplicatedSessionDevModeConfigurationTest {

    @Test
    void sessionSerializationDebugTool_productionModeAndDevModeSessionSerializationEnabled_notInstalled() {
        new ApplicationContextRunner()
                .withPropertyValues("vaadin.productionMode=true",
                        "vaadin.devmode.sessionSerialization.enabled=true")
                .withConfiguration(AutoConfigurations.of(
                        AzureKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_productionModeAndDevModeSessionSerializationDisabled_notInstalled() {
        new ApplicationContextRunner()
                .withPropertyValues("vaadin.productionMode=true",
                        "vaadin.devmode.sessionSerialization.enabled=false")
                .withConfiguration(AutoConfigurations.of(
                        AzureKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_productionModeAndDevModeSessionSerializationNotSet_notInstalled() {
        new ApplicationContextRunner()
                .withPropertyValues("vaadin.productionMode=true")
                .withConfiguration(AutoConfigurations.of(
                        AzureKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_productionModeNotSetAndDevModeSessionSerializationEnabled_notInstalled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "vaadin.devmode.sessionSerialization.enabled=true")
                .withConfiguration(AutoConfigurations.of(
                        AzureKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .hasSize(1));
    }

    @Test
    void sessionSerializationDebugTool_productionModeNotSetAndDevModeSessionSerializationDisabled_notInstalled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "vaadin.devmode.sessionSerialization.enabled=false")
                .withConfiguration(AutoConfigurations.of(
                        AzureKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_productionModeNotSetAndDevModeSessionSerializationNotSet_notInstalled() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                AzureKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_notProductionModeAndDevModeSessionSerializationEnabled_notInstalled() {
        new ApplicationContextRunner()
                .withPropertyValues("vaadin.productionMode=false",
                        "vaadin.devmode.sessionSerialization.enabled=true")
                .withConfiguration(AutoConfigurations.of(
                        AzureKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .hasSize(1));
    }

    @Test
    void sessionSerializationDebugTool_notProductionModeAndDevModeSessionSerializationDisabled_notInstalled() {
        new ApplicationContextRunner()
                .withPropertyValues("vaadin.productionMode=false",
                        "vaadin.devmode.sessionSerialization.enabled=false")
                .withConfiguration(AutoConfigurations.of(
                        AzureKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_notProductionModeAndDevModeSessionSerializationNotSet_notInstalled() {
        new ApplicationContextRunner()
                .withPropertyValues("vaadin.productionMode=false")
                .withConfiguration(AutoConfigurations.of(
                        AzureKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

}
