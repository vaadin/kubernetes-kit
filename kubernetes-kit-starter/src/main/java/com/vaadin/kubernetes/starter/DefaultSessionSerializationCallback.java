package com.vaadin.kubernetes.starter;

import com.vaadin.kubernetes.starter.sessiontracker.SessionSerializationCallback;

/**
 * Default error callbacks that are called after a serialization or deserialization
 * error happens.
 */
public class DefaultSessionSerializationCallback implements SessionSerializationCallback {

    @Override
    public void onSerializationError(Exception ex) throws Exception {
        throw ex;
    }

    @Override
    public void onDeserializationError(Exception ex) throws Exception {
        throw ex;
    }
}
