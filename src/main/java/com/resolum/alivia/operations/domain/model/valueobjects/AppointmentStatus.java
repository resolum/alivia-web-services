package com.resolum.alivia.operations.domain.model.valueobjects;

public enum AppointmentStatus {
    SCHEDULED,
    /** The technician attended the visit and recorded its result. */
    COMPLETED,
    CANCELED
}
