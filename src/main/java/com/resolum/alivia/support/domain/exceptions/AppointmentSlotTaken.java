package com.resolum.alivia.support.domain.exceptions;

public class AppointmentSlotTaken extends RuntimeException {
    public AppointmentSlotTaken() {
        super("The selected window is no longer available. Choose another one.");
    }
}
