package com.resolum.alivia.operations.domain.model.commands;

/** The administrator makes {@code technicianId} responsible for the order, keeping its block. */
public record AssignTechnicianCommand(Long orderId, Long technicianId, String note) {
}
