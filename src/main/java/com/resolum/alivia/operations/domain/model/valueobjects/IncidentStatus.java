package com.resolum.alivia.operations.domain.model.valueobjects;

public enum IncidentStatus {
    /** Reported and waiting for attention (Registrada). */
    OPEN,
    /** A technician is evaluating the report to plan the visit (En evaluación). */
    IN_EVALUATION,
    /** A maintenance visit is scheduled for it. */
    SCHEDULED,
    /** The technician arrived and is attending it. */
    IN_PROGRESS,
    /** The maintenance passed its test and the service works again. */
    RESOLVED
}
