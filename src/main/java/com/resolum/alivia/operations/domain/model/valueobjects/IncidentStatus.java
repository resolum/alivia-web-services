package com.resolum.alivia.operations.domain.model.valueobjects;

public enum IncidentStatus {
    /** Reported and waiting for attention. */
    OPEN,
    /** A maintenance visit is scheduled for it. */
    SCHEDULED,
    /** The maintenance passed its test and the service works again. */
    RESOLVED
}
