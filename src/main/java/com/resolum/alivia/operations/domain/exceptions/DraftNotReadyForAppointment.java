package com.resolum.alivia.operations.domain.exceptions;

public class DraftNotReadyForAppointment extends RuntimeException {
    public DraftNotReadyForAppointment() {
        super("The card must be preauthorized before booking the technical evaluation.");
    }
}
