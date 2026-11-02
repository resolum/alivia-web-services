package com.resolum.alivia.operations.domain.exceptions;

public class TechnicalOrderNotScheduled extends RuntimeException {
    public TechnicalOrderNotScheduled() {
        super("Only scheduled orders can change their technician.");
    }
}
