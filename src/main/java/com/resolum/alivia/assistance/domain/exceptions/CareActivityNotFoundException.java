package com.resolum.alivia.assistance.domain.exceptions;

public class CareActivityNotFoundException extends RuntimeException {
    public CareActivityNotFoundException(Long id) {
        super("Care activity with ID " + id + " was not found.");
    }
}
