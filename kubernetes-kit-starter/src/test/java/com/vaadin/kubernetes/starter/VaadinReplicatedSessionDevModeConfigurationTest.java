package com.vaadin.kubernetes.starter;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.SerializationDebugRequestHandler;
import com.vaadin.kubernetes.starter.test.DisableOnJavaIOReflection;
import com.vaadin.kubernetes.starter.test.EnableOnJavaIOReflection;

@EnableOnJavaIOReflection
class VaadinReplicatedSessionDevModeConfigurationTest {

    @Test
    void sessionSerializationDebugTool_productionModeAndDevModeSessionSerializationEnabled_notInstalled() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "sun.io.serialization.extendedDebugInfo=true")
                .withPropertyValues("vaadin.productionMode=true",
                        "vaadin.devmode.sessionSerialization.enabled=true")
                .withBean(SerializationProperties.class)
                .withConfiguration(AutoConfigurations.of(
                        KubernetesKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_productionModeAndDevModeSessionSerializationDisabled_notInstalled() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "sun.io.serialization.extendedDebugInfo=true")
                .withPropertyValues("vaadin.productionMode=true",
                        "vaadin.devmode.sessionSerialization.enabled=false")
                .withBean(SerializationProperties.class)
                .withConfiguration(AutoConfigurations.of(
                        KubernetesKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_productionModeAndDevModeSessionSerializationNotSet_notInstalled() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "sun.io.serialization.extendedDebugInfo=true")
                .withPropertyValues("vaadin.productionMode=true")
                .withBean(SerializationProperties.class)
                .withConfiguration(AutoConfigurations.of(
                        KubernetesKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_productionModeNotSetAndDevModeSessionSerializationEnabled_installed() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "sun.io.serialization.extendedDebugInfo=true")
                .withPropertyValues(
                        "vaadin.devmode.sessionSerialization.enabled=true")
                .withBean(SerializationProperties.class)
                .withConfiguration(AutoConfigurations.of(
                        KubernetesKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .hasSize(1));
    }

    @Test
    void sessionSerializationDebugTool_productionModeNotSetAndDevModeSessionSerializationDisabled_notInstalled() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "sun.io.serialization.extendedDebugInfo=true")
                .withPropertyValues(
                        "vaadin.devmode.sessionSerialization.enabled=false")
                .withBean(SerializationProperties.class)
                .withConfiguration(AutoConfigurations.of(
                        KubernetesKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_productionModeNotSetAndDevModeSessionSerializationNotSet_notInstalled() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "sun.io.serialization.extendedDebugInfo=true")
                .withBean(SerializationProperties.class)
                .withConfiguration(AutoConfigurations.of(
                        KubernetesKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_notProductionModeAndDevModeSessionSerializationEnabled_installed() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "sun.io.serialization.extendedDebugInfo=true")
                .withPropertyValues("vaadin.productionMode=false",
                        "vaadin.devmode.sessionSerialization.enabled=true")
                .withBean(SerializationProperties.class)
                .withConfiguration(AutoConfigurations.of(
                        KubernetesKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .hasSize(1));
    }

    @Test
    void sessionSerializationDebugTool_notProductionModeAndDevModeSessionSerializationDisabled_notInstalled() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "sun.io.serialization.extendedDebugInfo=true")
                .withPropertyValues("vaadin.productionMode=false",
                        "vaadin.devmode.sessionSerialization.enabled=false")
                .withBean(SerializationProperties.class)
                .withConfiguration(AutoConfigurations.of(
                        KubernetesKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_notProductionModeAndDevModeSessionSerializationNotSet_notInstalled() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "sun.io.serialization.extendedDebugInfo=true")
                .withPropertyValues("vaadin.productionMode=false")
                .withBean(SerializationProperties.class)
                .withConfiguration(AutoConfigurations.of(
                        KubernetesKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @Test
    void sessionSerializationDebugTool_reflectionOnJavaIOAllowedAndExtendedDebugInfoSystemPropertyActive_installed() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "sun.io.serialization.extendedDebugInfo=true")
                .withPropertyValues(
                        "vaadin.devmode.sessionSerialization.enabled=true")
                .withBean(SerializationProperties.class)
                .withConfiguration(AutoConfigurations.of(
                        KubernetesKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .hasSize(1));
    }

    @Test
    void sessionSerializationDebugTool_reflectionOnJavaIOAllowedAndExtendedDebugInfoSystemPropertyNotActive_installed() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "sun.io.serialization.extendedDebugInfo=false")
                .withPropertyValues(
                        "vaadin.devmode.sessionSerialization.enabled=true")
                .withBean(SerializationProperties.class)
                .withConfiguration(AutoConfigurations.of(
                        KubernetesKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

    @DisableOnJavaIOReflection
    @Test
    void sessionSerializationDebugTool_reflectionOnJavaIONotAllowed_notInstalled() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "sun.io.serialization.extendedDebugInfo=true")
                .withPropertyValues(
                        "vaadin.devmode.sessionSerialization.enabled=true")
                .withBean(SerializationProperties.class)
                .withConfiguration(AutoConfigurations.of(
                        KubernetesKitConfiguration.VaadinReplicatedSessionDevModeConfiguration.class))
                .run(appCtx -> Assertions.assertThat(appCtx.getBeanNamesForType(
                        SerializationDebugRequestHandler.InitListener.class))
                        .isEmpty());
    }

}
