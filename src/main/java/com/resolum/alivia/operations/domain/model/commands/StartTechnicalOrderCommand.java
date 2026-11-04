package com.resolum.alivia.operations.domain.model.commands;

/** The technician arrived at the home and starts attending the order. */
public record StartTechnicalOrderCommand(Long orderId) {
}
