package com.resolum.alivia.assistance.domain.exceptions;

public class HandoverNotFoundException extends RuntimeException {
    public HandoverNotFoundException(Long id) {
        super("Handover with id " + id + " was not found.");
    }
}
