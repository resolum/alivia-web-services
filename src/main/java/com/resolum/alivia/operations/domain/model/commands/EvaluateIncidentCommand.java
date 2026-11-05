package com.resolum.alivia.operations.domain.model.commands;

/** The technician of the incident's maintenance order reviewed the report (En evaluación). */
public record EvaluateIncidentCommand(Long orderId) {
}
