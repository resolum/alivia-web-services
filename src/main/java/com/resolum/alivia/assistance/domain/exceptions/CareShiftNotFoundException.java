package com.resolum.alivia.assistance.domain.exceptions;

public class CareShiftNotFoundException extends RuntimeException {
    public CareShiftNotFoundException(Long id) {
        super("Care shift with id " + id + " was not found.");
    }
}
