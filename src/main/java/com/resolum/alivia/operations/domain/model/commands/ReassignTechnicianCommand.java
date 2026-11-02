package com.resolum.alivia.operations.domain.model.commands;

/** The responsible technician stopped being available; the backend chooses another one. */
public record ReassignTechnicianCommand(Long orderId, String reason) {
}
