package com.resolum.alivia.operations.domain.model.valueobjects;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;

import java.time.LocalDate;
import java.util.List;

/** A date a technician works, with the shift blocks of their work schedule. */
public record WorkingDay(LocalDate date, List<TimeRange> shifts) {
    public WorkingDay {
        shifts = List.copyOf(shifts);
    }
}
