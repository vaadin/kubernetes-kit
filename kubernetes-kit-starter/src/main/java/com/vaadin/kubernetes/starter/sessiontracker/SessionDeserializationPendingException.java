package com.vaadin.kubernetes.starter.sessiontracker;

public class SessionDeserializationPendingException extends RuntimeException {
    public SessionDeserializationPendingException(String message) {
        super(message);
    }
}
