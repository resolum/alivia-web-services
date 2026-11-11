package com.resolum.alivia.communications.domain.exceptions;

public class InvalidMessageContent extends RuntimeException {
    public InvalidMessageContent() {
        super("Message content (title and body) cannot be empty.");
    }
}
