package com.resolum.alivia.operations.domain.exceptions;

public class TechnicalOrderAccessDenied extends RuntimeException {
    public TechnicalOrderAccessDenied() {
        super("Only the technician assigned to the order can attend it.");
    }
}
