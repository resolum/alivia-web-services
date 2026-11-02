package com.resolum.alivia.operations.domain.model.queries;

import java.time.LocalDate;

/** Bookable windows from {@code from} (null: tomorrow) for {@code days} days. */
public record GetAvailableSlotsQuery(LocalDate from, int days) {
}
