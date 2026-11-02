package com.resolum.alivia.hcm.domain.model.valueobjects;

import java.time.LocalDate;
import java.util.Objects;

public record ContractPeriod(LocalDate startDate, LocalDate endDate) {
    public ContractPeriod {
        Objects.requireNonNull(startDate, "Contract start date is required.");
        Objects.requireNonNull(endDate, "Contract end date is required.");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Contract end date cannot be before its start date.");
        }
    }

    public boolean contains(LocalDate date) {
        return date != null && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean overlapsWith(ContractPeriod other) {
        Objects.requireNonNull(other, "Contract period is required.");
        return !startDate.isAfter(other.endDate) && !other.startDate.isAfter(endDate);
    }
}
