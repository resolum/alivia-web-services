package com.resolum.alivia.operations.domain.model.valueobjects;

/** Why a technician became responsible for an order. */
public enum AssignmentReason {
    /** Reserved automatically when the order was created (the least busy technician of the block). */
    BOOKED,
    /** Chosen by the administrator among the eligible technicians. */
    ASSIGNED,
    /** The previous technician stopped being available and the backend chose another one. */
    REASSIGNED
}
