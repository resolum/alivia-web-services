package com.resolum.alivia.assistance.domain.exceptions;

public class CareActivityNotActiveException extends RuntimeException {
    public CareActivityNotActiveException(Long id) {
        super("Care activity with ID " + id + " is not active.");
    }
}
