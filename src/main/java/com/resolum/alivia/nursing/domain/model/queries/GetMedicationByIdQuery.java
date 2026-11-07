package com.resolum.alivia.nursing.domain.model.queries;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.Objects;

/**
 * Query to get a scheduled medication by its identifier.
 */
public record GetMedicationByIdQuery(
        UserId userId,
        Long medicationId
) {
    public GetMedicationByIdQuery {
        Objects.requireNonNull(userId, "User id cannot be null");
        Objects.requireNonNull(medicationId, "Medication id cannot be null");
    }
}
