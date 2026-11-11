package com.resolum.alivia.assistance.domain.exceptions;

public class AssistedPersonNotFoundException extends RuntimeException {
    public AssistedPersonNotFoundException(Long id) {
        super("Assisted person with ID " + id + " was not found.");
    }
}
