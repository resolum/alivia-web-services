package com.resolum.alivia.operations.domain.exceptions;

public class TechnicianNotEligible extends RuntimeException {
    public TechnicianNotEligible() {
        super("The technician does not work the whole block or already has a visit in it.");
    }
}
