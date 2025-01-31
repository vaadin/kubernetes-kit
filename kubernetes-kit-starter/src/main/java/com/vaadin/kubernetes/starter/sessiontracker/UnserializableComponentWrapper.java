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
import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.dom.ElementUtil;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.internal.StateTree;

@Tag(Tag.DIV)
public class UnserializableComponentWrapper<S extends Serializable, T extends Component>
        extends Component {

    private transient T component;
    private S state;
    private SerializableFunction<T, S> componentSerializer;
    private SerializableFunction<S, T> componentDeserializer;

    public UnserializableComponentWrapper(T component) {
        getElement().appendChild(component.getElement());
        this.component = component;
    }

    public static <S extends Serializable, T extends Component> UnserializableComponentWrapper<S, T> of(
            T component) {
        return new UnserializableComponentWrapper<>(component);
    }

    public UnserializableComponentWrapper<S, T> withComponentSerializer(
            SerializableFunction<T, S> componentSerializer) {
        this.componentSerializer = componentSerializer;
        return this;
    }

    public UnserializableComponentWrapper<S, T> withComponentDeserializer(
            SerializableFunction<S, T> componentDeserializer) {
        this.componentDeserializer = componentDeserializer;
        return this;
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream out)
            throws IOException {
        state = componentSerializer.apply(component);
        if (!component.isAttached()) {
            out.defaultWriteObject();
        } else {
            throw new IllegalStateException(
                    component + " component is still attached");
        }
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        in.registerValidation(() -> {
            getUI().map(UI::getSession).ifPresent(session -> {
                Runnable cleaner = SessionUtil.injectLockIfNeeded(session);
                try {
                    component = componentDeserializer.apply(state);
                    getElement().appendChild(component.getElement());
                } finally {
                    cleaner.run();
                }
            });
        }, 0);
    }

    static void beforeSerialization(UI ui) {
        doWithWrapper(ui, wrapper -> {
            wrapper.component.removeFromParent();
            flush(wrapper);
        });
    }

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
