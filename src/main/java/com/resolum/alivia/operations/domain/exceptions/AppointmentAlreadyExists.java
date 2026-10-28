package com.resolum.alivia.operations.domain.exceptions;

public class AppointmentAlreadyExists extends RuntimeException {
    public AppointmentAlreadyExists() {
        super("This configuration already has a technical evaluation scheduled.");
    }
}
