package com.resolum.alivia.communications.domain.exceptions;

public class InvalidPushToken extends RuntimeException {
    public InvalidPushToken() {
        super("Push token cannot be empty or invalid.");
    }
}
