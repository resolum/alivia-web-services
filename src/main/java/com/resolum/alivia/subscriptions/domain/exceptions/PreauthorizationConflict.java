package com.resolum.alivia.subscriptions.domain.exceptions;

public class PreauthorizationConflict extends RuntimeException {
    public PreauthorizationConflict(String message) { super(message); }
}
