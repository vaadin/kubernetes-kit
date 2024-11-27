/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.vaadin.flow.internal.ReflectTools;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import com.vaadin.kubernetes.starter.sessiontracker.PessimisticSerializationRequiredException;

/**
 * Spring specific implementation of {@link TransientHandler}, capable to
 * inspect and inject Spring Beans into transient fields.
 *
 * Inspection finds all transient fields whose actual value matches a Spring
 * managed bean. The bean name is stored into {@link TransientDescriptor} and it
 * is used on injection phase to lookup the correct bean.
 */
public class SpringTransientHandler implements TransientHandler {

    private final ApplicationContext appCtx;

    public SpringTransientHandler(ApplicationContext appCtx) {
        this.appCtx = appCtx;
    }

    @Override
    public void inject(Object obj, List<TransientDescriptor> transients) {
        Class<?> type = obj.getClass();
        Map<Class<?>, List<TransientDescriptor>> fieldsByClass = transients
                .stream().collect(Collectors
                        .groupingBy(TransientDescriptor::getDeclaringClass));
        while (type != Object.class && !fieldsByClass.isEmpty()) {
            List<TransientDescriptor> descriptors = fieldsByClass.remove(type);
            if (descriptors != null) {
                descriptors.forEach(descr -> injectField(obj, descr));
            }
            type = type.getSuperclass();
        }
    }

    private void injectField(Object obj, TransientDescriptor descriptor) {
        getLogger().debug(
                "Injecting '{}' into transient field '{}' of type '{}'",
                descriptor.getInstanceReference(), descriptor.getName(),
                obj.getClass());
        try {
            ReflectTools.setJavaFieldValue(obj, descriptor.getField(),
                    appCtx.getBean(descriptor.getInstanceReference()));
        } catch (RuntimeException ex) {
            getLogger().error(
                    "Failed injecting '{}' into transient field '{}' of type '{}'",
                    descriptor.getInstanceReference(), descriptor.getName(),
                    obj.getClass());
            throw ex;
        }
    }

    public List<TransientDescriptor> inspect(Object target) {
        List<Injectable> injectables = findTransientFields(target.getClass(),
                f -> true).stream().map(field -> detectBean(target, field))
                .filter(Objects::nonNull).toList();
        return createDescriptors(target, injectables);
    }

    private Injectable detectBean(Object target, Field field) {
        Object value = getFieldValue(target, field);
        if (value != null) {
            Class<?> valueType = value.getClass();
            getLogger().trace(
                    "Inspecting field {} of class {} for injected beans",
                    field.getName(), target.getClass());
            Set<String> beanNames = new LinkedHashSet<>(List
                    .of(appCtx.getBeanNamesForType(valueType, true, false)));
            List<String> vaadinScopedBeanNames = new ArrayList<>();
            Collections.addAll(vaadinScopedBeanNames,
                    appCtx.getBeanNamesForAnnotation(VaadinSessionScope.class));
            Collections.addAll(vaadinScopedBeanNames,
                    appCtx.getBeanNamesForAnnotation(UIScope.class));
            Collections.addAll(vaadinScopedBeanNames,
                    appCtx.getBeanNamesForAnnotation(RouteScope.class));

            boolean vaadinScoped = beanNames.stream()
                    .anyMatch(vaadinScopedBeanNames::contains);
            if (vaadinScoped && VaadinSession.getCurrent() == null) {
                getLogger().warn(
                        "VaadinSession is not available when trying to inspect Vaadin scoped bean: {}."
                                + "Transient fields might not be registered for deserialization.",
                        beanNames);
                beanNames.removeIf(vaadinScopedBeanNames::contains);
            }
            return new Injectable(field, value, beanNames, vaadinScoped);
        }
        getLogger().trace(
                "No bean detected for field {} of class {}, field value is null",
                field.getName(), target.getClass());
        return null;
    }

    private record Injectable(Field field, Object value, Set<String> beanNames,
            boolean vaadinScoped) {
    }

    private TransientDescriptor createDescriptor(Object target,
            Injectable injectable) {
        Field field = injectable.field;
        Object value = injectable.value;
        Class<?> valueType = value.getClass();
        TransientDescriptor transientDescriptor;
        transientDescriptor = injectable.beanNames.stream()
                .map(beanName -> Map.entry(beanName, appCtx.getBean(beanName)))
                .filter(e -> e.getValue() == value || matchesPrototype(
                        e.getKey(), e.getValue(), valueType))
                .map(Map.Entry::getKey).findFirst()
                .map(beanName -> new TransientDescriptor(field, beanName,
                        injectable.vaadinScoped))
                .orElse(null);
        if (transientDescriptor != null) {
            getLogger().trace("Bean {} found for field {} of class {}",
                    transientDescriptor.getInstanceReference(), field.getName(),
                    target.getClass());
        } else {
            getLogger().trace("No bean detected for field {} of class {}",
                    field.getName(), target.getClass());
        }
        return transientDescriptor;
    }

    private List<TransientDescriptor> createDescriptors(Object target,
            List<Injectable> injectables) {
        boolean sessionLocked = false;
        if (injectables.stream().anyMatch(Injectable::vaadinScoped)) {
            // Bean has Vaadin scope, lookup needs VaadinSession lock
            VaadinSession vaadinSession = VaadinSession.getCurrent();
            if (vaadinSession != null) {
                try {
                    sessionLocked = vaadinSession.getLockInstance().tryLock(1,
                            TimeUnit.SECONDS);
                    if (!sessionLocked) {
                        throw new PessimisticSerializationRequiredException(
                                "Unable to acquire VaadinSession lock to lookup Vaadin scoped beans. "
                                        + collectVaadinScopedCandidates(
                                                injectables));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new PessimisticSerializationRequiredException(
                            "Unable to acquire VaadinSession lock to lookup Vaadin scoped beans. "
                                    + collectVaadinScopedCandidates(
                                            injectables),
                            e);
                }
            }
        }
        try {
            return injectables.stream()
                    .map(injectable -> createDescriptor(target, injectable))
                    .filter(Objects::nonNull).toList();
        } finally {
            if (sessionLocked) {
                VaadinSession.getCurrent().getLockInstance().unlock();
            }
        }
    }

    private String collectVaadinScopedCandidates(List<Injectable> injectables) {
        return injectables.stream().filter(Injectable::vaadinScoped)
                .map(injectable -> String.format(
                        "[Field: %s, bean candidates: %s]",
                        injectable.field.getName(), injectable.beanNames))
                .collect(Collectors.joining(", "));
    }

    private boolean matchesPrototype(String beanName, Object beanDefinition,
            Class<?> fieldValueType) {
        return appCtx.containsBeanDefinition(beanName)
                && appCtx.isPrototype(beanName)
                && beanDefinition.getClass() == fieldValueType;
    }

    private Object getFieldValue(Object target, Field field) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (InaccessibleObjectException | IllegalAccessException e) {
            // TODO: InaccessibleObjectException happens with Java 17
            // when inspecting Vaadin NodeMap$HashMapValues that extends HashMap
            // Should we exclude some packages by default?
            // Should we throw or ignore the error?
            getLogger().trace("Cannot access field {} of class {}",
                    field.getName(), target.getClass(), e);
        }
        return null;
    }

    private List<Field> findTransientFields(Class<?> type,
            Predicate<Field> includeField) {
        List<Field> transientFields = new ArrayList<>();
        while (type != Object.class) {
            Stream.of(type.getDeclaredFields())
                    .filter(f -> Modifier.isTransient(f.getModifiers()))
                    .filter(includeField)
                    .collect(Collectors.toCollection(() -> transientFields));
            type = type.getSuperclass();
        }
        return transientFields;
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(SpringTransientHandler.class);
    }

}
