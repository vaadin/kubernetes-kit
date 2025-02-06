/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.dom.ElementUtil;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.internal.StateTree;

/**
 * A wrapper component that allows an otherwise unserializable {@link Component}
 * to be serialized and deserialized using the provided serializer and
 * deserializer functions.
 * <p>
 * During serialization, the serializer generates a serializable state object
 * from the wrapped component. This state object is intended to store
 * serializable and cacheable properties of the component. Upon deserialization,
 * the deserializer reconstructs the component from scratch using the state
 * object, after the entire graph has been restored. Developers are responsible
 * for ensuring that the necessary component properties are properly persisted
 * and restored.
 * <p>
 * Unserializable components are temporarily removed from the component tree
 * during serialization and reinserted after deserialization. Any {@link UI}
 * changes caused by their removal and re-addition are silently ignored.
 * <p>
 * <b>Important Note:</b> Any attach or detach listeners registered on the
 * wrapped component will still be triggered.
 *
 * @param <S>
 *            the type of the state object that is created with the serializer
 * @param <T>
 *            the type of the wrapped {@link Component}
 */
@Tag(Tag.DIV)
public class UnserializableComponentWrapper<S extends Serializable, T extends Component>
        extends Component {

    private transient T component;
    private S state;
    private final SerializableFunction<T, S> serializer;
    private final SerializableFunction<S, T> deserializer;

    /**
     * Constructs a new unserializable component wrapper instance.
     *
     * @param component
     *            the unserializable {@link Component} to be wrapped
     * @param serializer
     *            the serializer function that generates the serializable state
     *            object from the wrapped {@link Component} during the
     *            serialization
     *
     * @param deserializer
     *            the deserializer function that reconstructs the
     *            {@link Component} from scratch using the state object, after
     *            the entire graph has been restored upon the deserialization
     */
    public UnserializableComponentWrapper(T component,
            SerializableFunction<T, S> serializer,
            SerializableFunction<S, T> deserializer) {
        this.component = Objects.requireNonNull(component);
        this.serializer = Objects.requireNonNull(serializer);
        this.deserializer = Objects.requireNonNull(deserializer);
        getElement().appendChild(component.getElement());
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream out)
            throws IOException {
        state = serializer.apply(component);
        out.defaultWriteObject();
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        in.registerValidation(this::restoreComponent, 0);
    }

    private void restoreComponent() {
        getUI().map(UI::getSession).ifPresent(session -> {
            Runnable cleaner = SessionUtil.injectLockIfNeeded(session);
            try {
                component = deserializer.apply(state);
                getElement().appendChild(component.getElement());
            } finally {
                cleaner.run();
            }
        });
    }

    /**
     * Prepares the UI for serialization, removing unserializable components
     * from the component tree.
     * <p>
     * The changes to the UI caused by the removal are silently ignored.
     * <p>
     * <b>IMPORTANT NOTE:</b> Any detach listener registered on the wrapped
     * components will be executed.
     *
     * @param ui
     *            the {@link UI} to prepare for serialization
     */
    static void beforeSerialization(UI ui) {
        doWithWrapper(ui, wrapper -> {
            wrapper.component.removeFromParent();
            flush(wrapper);
        });
    }

    /**
     * Restores the UI adding the unserializable components to the component
     * tree.
     * <p>
     * The changes to the UI caused by re-adding the components are silently
     * ignored.
     * <p>
     * <b>IMPORTANT NOTE:</b> Any attach listener registered on the wrapped
     * components will be executed.
     *
     * @param ui
     *            the {@link UI} to prepare for serialization
     */
    static void afterSerialization(UI ui) {
        doWithWrapper(ui, wrapper -> {
            wrapper.state = null;
            wrapper.getElement().appendChild(wrapper.component.getElement());
            flush(wrapper);
        });
    }

    private static void flush(UnserializableComponentWrapper<?, ?> wrapper) {
        if (wrapper.getElement().getNode()
                .getOwner() instanceof StateTree owner) {
            owner.collectChanges(change -> {
                // The collector does nothing to prevent changes to the tree to
                // be sent to the client
            });
        }
    }

    /**
     * Collects all {@link UnserializableComponentWrapper} objects from the
     * {@link UI} and applies the provided action on each of them.
     *
     * @param ui
     *            the {@link UI} to collect the
     *            {@link UnserializableComponentWrapper} objects from
     * @param action
     *            the action to apply on all
     *            {@link UnserializableComponentWrapper}
     */
    @SuppressWarnings("rawtypes")
    static void doWithWrapper(UI ui,
            Consumer<UnserializableComponentWrapper> action) {
        ui.getElement().getNode().visitNodeTree(node -> ElementUtil.from(node)
                .flatMap(Element::getComponent)
                .filter(UnserializableComponentWrapper.class::isInstance)
                .map(UnserializableComponentWrapper.class::cast)
                .ifPresent(action));
    }
}
