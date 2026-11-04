package com.resolum.alivia.operations.domain.exceptions;

public class TechnicalOrderNotScheduled extends RuntimeException {
    public TechnicalOrderNotScheduled() {
        super("Only scheduled orders can be changed.");
    }
}
