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

import java.io.Serializable;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.internal.StateTree;

@Tag(Tag.DIV)
public class UnserializableComponentWrapper<S extends Serializable, T extends Component>
        extends Component {

    private transient T component;
    private S state;
    private SerializableFunction<S, T> generator;
    private SerializableFunction<T, S> saver;

    public UnserializableComponentWrapper(T component) {
        getElement().appendChild(component.getElement());
        this.component = component;
    }

    public static <S extends Serializable, T extends Component> UnserializableComponentWrapper<S, T> of(
            T component) {
        return new UnserializableComponentWrapper<>(component);
    }

    public UnserializableComponentWrapper<S, T> withGenerator(
            SerializableFunction<S, T> generator) {
        this.generator = generator;
        return this;
    }

    public UnserializableComponentWrapper<S, T> withSaver(
            SerializableFunction<T, S> saver) {
        this.saver = saver;
        return this;
    }

    public void beforeSerialize() {
        state = saver.apply(component);
        component.removeFromParent();
        flush(getElement());
    }

    public void afterSerialize() {
        getElement().appendChild(component.getElement());
        flush(getElement());
    }

    public void afterDeserialize() {
        component = generator.apply(state);
        state = null;
        getElement().appendChild(component.getElement());
    }

    private void flush(Element element) {
        if (element.getNode().getOwner() instanceof StateTree owner) {
            owner.collectChanges(change -> {
            });
        }
    }
}
