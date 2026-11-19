package com.resolum.alivia.operations.domain.model.events;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;

/**
 * A technician became responsible for a technical order, or the order of the same technician moved to another block.
 *
 * @param order  the order, already with its new technician and block
 * @param change whether the technician is new to the order or the order was rescheduled
 */
public record TechnicalOrderAssignedEvent(Appointment order, Change change) {

    public enum Change {
        /** The technician is new to the order (booking, administrator's choice or reassignment). */
        ASSIGNED,
        /** The technician keeps the order, which moved to another block. */
        RESCHEDULED
    }
}
