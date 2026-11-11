package com.resolum.alivia.assistance.domain.exceptions;

public class BusinessNotFoundException extends RuntimeException {
    public BusinessNotFoundException(Long id) {
        super("Business with ID " + id + " was not found.");
    }
}
