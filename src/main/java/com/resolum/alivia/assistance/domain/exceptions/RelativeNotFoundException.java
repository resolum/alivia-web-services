package com.resolum.alivia.assistance.domain.exceptions;

public class RelativeNotFoundException extends RuntimeException {
    public RelativeNotFoundException(Long id) {
        super("Relative with ID " + id + " was not found.");
    }
}
