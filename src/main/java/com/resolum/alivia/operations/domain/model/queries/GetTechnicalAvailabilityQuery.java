package com.resolum.alivia.operations.domain.model.queries;

import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;

import java.time.LocalDate;

/** Blocks of {@code serviceType} with capacity from {@code from} (null: tomorrow) for {@code days} days. */
public record GetTechnicalAvailabilityQuery(ServiceType serviceType, LocalDate from, int days) {
}
