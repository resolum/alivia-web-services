package com.resolum.alivia.subscriptions.domain.exceptions;

public class PreauthorizationUnavailable extends RuntimeException {
    public PreauthorizationUnavailable(String message) { super(message); }
    public PreauthorizationUnavailable(String message, Throwable cause) { super(message, cause); }
}
