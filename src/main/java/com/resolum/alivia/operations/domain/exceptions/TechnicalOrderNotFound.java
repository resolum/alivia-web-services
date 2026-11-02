package com.resolum.alivia.operations.domain.exceptions;

public class TechnicalOrderNotFound extends RuntimeException {
    public TechnicalOrderNotFound(Long orderId) {
        super("The technical order %d does not exist.".formatted(orderId));
    }
}
