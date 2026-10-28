package com.resolum.alivia.support.domain.model.valueobjects;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;

import java.util.List;

/** A technician and the shift blocks they work on one date, as Support needs them. */
public record TechnicianAvailability(Long technicianId, String fullName, List<TimeRange> shifts) {
    public TechnicianAvailability {
        shifts = List.copyOf(shifts);
    }
}
