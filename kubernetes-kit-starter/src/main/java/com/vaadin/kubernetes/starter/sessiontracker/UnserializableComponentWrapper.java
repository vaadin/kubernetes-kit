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
 * A wrapper component that can be used to wrap an unserializable
 * {@link Component} to make it serializable and deserializable using the
 * provided serializer and deserializer functions.
 * <p>
 * The serializer is used to create a serializable state object from the
 * provided component during the serialization process and the deserializer is
 * used to regenerate the component from the state object after the entire graph
 * has been deserialized and reconstituted.
 * <p>
 * The unserializable components are removed from the component tree during the
 * serialization, and they are re-added after the deserialization. The changes
 * to the {@link UI} caused by the removal and re-adding the components are
 * silently ignored.
 * <p>
 * <b>IMPORTANT NOTE:</b> Any detach listener registered on the wrapped
 * component will be executed.
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
    private SerializableFunction<T, S> serializer;
    private SerializableFunction<S, T> deserializer;

    /**
     * Constructs a new unserializable component wrapper instance.
     *
     * @param component
     *            the unserializable {@link Component} to be wrapped
     */
    public UnserializableComponentWrapper(T component) {
        this.component = Objects.requireNonNull(component);
        getElement().appendChild(component.getElement());
    }

    /**
     * The serializer function that creates the serializable state object from
     * the provided {@link Component} during the serialization process.
     *
     * @param serializer
     *            the serializer function
     * @return the unserializable component wrapper itself
     */
    public UnserializableComponentWrapper<S, T> withComponentSerializer(
            SerializableFunction<T, S> serializer) {
        this.serializer = Objects.requireNonNull(serializer);
        return this;
    }

    /**
     * The deserializer function that is used to regenerate the
     * {@link Component} from the state object after the entire graph has been
     * deserialized and reconstituted.
     *
     * @param deserializer
     *            the deserializer function
     * @return the unserializable component wrapper itself
     */
    public UnserializableComponentWrapper<S, T> withComponentDeserializer(
            SerializableFunction<S, T> deserializer) {
        this.deserializer = Objects.requireNonNull(deserializer);
        return this;
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
        in.registerValidation(
                () -> getUI().map(UI::getSession).ifPresent(session -> {
                    Runnable cleaner = SessionUtil.injectLockIfNeeded(session);
                    try {
                        component = deserializer.apply(state);
                        getElement().appendChild(component.getElement());
                    } finally {
                        cleaner.run();
                    }
                }), 0);
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
