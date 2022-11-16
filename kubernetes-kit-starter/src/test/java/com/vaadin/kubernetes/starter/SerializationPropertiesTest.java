package com.vaadin.kubernetes.starter;

import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.vaadin.kubernetes.starter.sessiontracker.SessionSerializer;
import com.vaadin.kubernetes.starter.sessiontracker.push.PushSessionTracker;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.SpringTransientHandler;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientDescriptor;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.ViewAccessChecker;
import com.vaadin.flow.shared.ui.Dependency;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerializationPropertiesTest {

    @Test
    void transientInjectableFilter_excludedPackages_classIsRejected() {
        SerializationProperties.TransientsProperties props = new SerializationProperties()
                .getTransients();
        props.getExcludePackages()
                .addAll(List.of(VaadinService.class.getPackageName(),
                        TransientDescriptor.class.getPackageName()));
        Predicate<Class<?>> filter = props.transientInjectableFilter();

        assertFalse(filter.test(VaadinService.class));
        assertFalse(filter.test(ViewAccessChecker.class));
        assertFalse(filter.test(SpringTransientHandler.class));

        assertTrue(filter.test(Dependency.class));
        assertTrue(filter.test(SessionSerializer.class));
        assertTrue(filter.test(PushSessionTracker.class));
    }

    @Test
    void transientInjectableFilter_includedPackages_classIsAccepted() {
        SerializationProperties.TransientsProperties props = new SerializationProperties()
                .getTransients();
        props.getIncludePackages()
                .addAll(List.of(VaadinService.class.getPackageName(),
                        TransientDescriptor.class.getPackageName()));
        Predicate<Class<?>> filter = props.transientInjectableFilter();

        assertTrue(filter.test(VaadinService.class));
        assertTrue(filter.test(ViewAccessChecker.class));
        assertTrue(filter.test(SpringTransientHandler.class));

        assertFalse(filter.test(Dependency.class));
        assertFalse(filter.test(SessionSerializer.class));
        assertFalse(filter.test(PushSessionTracker.class));
    }

    @Test
    void transientInjectableFilter_mixedPackageRules_classIsAccepted() {
        SerializationProperties.TransientsProperties props = new SerializationProperties()
                .getTransients();
        props.getExcludePackages()
                .addAll(List.of(ViewAccessChecker.class.getPackageName(),
                        TransientDescriptor.class.getPackageName()));
        props.getIncludePackages()
                .addAll(List.of(VaadinService.class.getPackageName(),
                        SessionSerializer.class.getPackageName()));

        Predicate<Class<?>> filter = props.transientInjectableFilter();

        assertTrue(filter.test(VaadinService.class));
        assertTrue(filter.test(SessionSerializer.class));
        assertTrue(filter.test(PushSessionTracker.class));

        assertFalse(filter.test(SpringTransientHandler.class));
        assertFalse(filter.test(ViewAccessChecker.class));
        assertFalse(filter.test(Dependency.class));
    }

    @Test
    void transientInjectableFilter_noRules_allClassesAreAccepted() {
        SerializationProperties.TransientsProperties props = new SerializationProperties()
                .getTransients();
        Predicate<Class<?>> filter = KubernetesKitConfiguration.VaadinReplicatedSessionConfiguration
                .withVaadinDefaultFilter(props.transientInjectableFilter());

        assertTrue(filter.test(VaadinService.class));
        assertTrue(filter.test(SessionSerializer.class));
        assertTrue(filter.test(PushSessionTracker.class));
        assertTrue(filter.test(SpringTransientHandler.class));
        assertTrue(filter.test(ViewAccessChecker.class));
        assertTrue(filter.test(Dependency.class));
    }

}
