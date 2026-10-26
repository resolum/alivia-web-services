package com.resolum.alivia.profiles.domain.exceptions;

public class StaffProfileNotFoundException extends RuntimeException {
    public StaffProfileNotFoundException(Long id) {
        super("Staff profile with id " + id + " not found");
    }

    public StaffProfileNotFoundException(String message) {
        super(message);
    }
}
