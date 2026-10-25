package com.resolum.alivia.profiles.domain.exceptions;

public class BusinessProfileNotFoundException extends RuntimeException {
    public BusinessProfileNotFoundException(Long id) {
        super("Business profile with id " + id + " not found");
    }

    public BusinessProfileNotFoundException(String message) {
        super(message);
    }
}
