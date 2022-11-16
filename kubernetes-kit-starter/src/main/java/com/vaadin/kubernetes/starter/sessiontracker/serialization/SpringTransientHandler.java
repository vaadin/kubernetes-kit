package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.vaadin.flow.internal.ReflectTools;

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
        getLogger().debug("Injecting '{}' into transient field {} of type {}",
                descriptor.getInstanceReference(), descriptor.getName(),
                obj.getClass());
        ReflectTools.setJavaFieldValue(obj, descriptor.getField(),
                appCtx.getBean(descriptor.getInstanceReference()));
    }

    public List<TransientDescriptor> inspect(Object target) {
        return findTransientFields(target.getClass(), f -> true).stream()
                .map(field -> detectBean(target, field))
                .filter(Objects::nonNull).collect(Collectors.toList());
    }

    private TransientDescriptor detectBean(Object target, Field field) {
        Object value = getFieldValue(target, field);

        if (value != null) {
            Class<?> valueType = value.getClass();
            getLogger().trace(
                    "Inspecting field {} of class {} for injected beans",
                    field.getName(), target.getClass());
            TransientDescriptor transientDescriptor = appCtx
                    .getBeansOfType(valueType).entrySet().stream()
                    .filter(e -> e.getValue() == value || matchesPrototype(
                            e.getKey(), e.getValue(), valueType))
                    .map(Map.Entry::getKey).findFirst()
                    .map(beanName -> new TransientDescriptor(field, beanName))
                    .orElse(null);
            if (transientDescriptor != null) {
                getLogger().trace("Bean {} found for field {} of class {}",
                        transientDescriptor.getInstanceReference(),
                        field.getName(), target.getClass());
            } else {
                getLogger().trace("No bean detected for field {} of class {}",
                        field.getName(), target.getClass());
            }
            return transientDescriptor;
        }
        getLogger().trace(
                "No bean detected for field {} of class {}, field value is null",
                field.getName(), target.getClass());
        return null;
    }

    private boolean matchesPrototype(String beanName, Object beanDefinition,
            Class<?> fieldValueType) {
        return appCtx.isPrototype(beanName)
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
            getLogger().debug("Cannot access field {} of class {}",
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
        return LoggerFactory
                .getLogger(TransientInjectableObjectInputStream.class);
    }

}
