package com.resolum.alivia.operations.domain.model.queries;

import java.time.LocalDate;

/**
 * Bookable windows from {@code from} (null: tomorrow) for {@code days} days. The window held by
 * {@code draftId}, when given, is still offered to that request.
 */
public record GetAvailableSlotsQuery(LocalDate from, int days, String draftId) {
    public GetAvailableSlotsQuery(LocalDate from, int days) {
        this(from, days, null);
    }
}
