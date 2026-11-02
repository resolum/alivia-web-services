package com.resolum.alivia.hcm.domain.model.valueobjects;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Validity of a work schedule. A missing end date means the schedule applies until
 * the associated contract ends.
 */
public record WorkSchedulePeriod(LocalDate startDate, LocalDate endDate) {
    public WorkSchedulePeriod {
        Objects.requireNonNull(startDate, "Work schedule start date is required.");
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Work schedule end date cannot be before its start date.");
        }
    }

    public boolean isOpenEnded() {
        return endDate == null;
    }

    public boolean fitsWithin(ContractPeriod contract) {
        return contract.contains(startDate) && (endDate == null || contract.contains(endDate));
    }

    public boolean contains(LocalDate date) {
        return date != null && !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    }
}
