package com.resolum.alivia.assistance.domain.exceptions;

public class DniAlreadyExistsException extends RuntimeException {
    public DniAlreadyExistsException(String dni) {
        super("DNI " + dni + " already exists.");
    }
}
