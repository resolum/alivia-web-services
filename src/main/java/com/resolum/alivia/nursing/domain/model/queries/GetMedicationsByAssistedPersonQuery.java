package com.resolum.alivia.nursing.domain.model.queries;

import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.Objects;

/**
 * Query to list scheduled medications for an assisted person.
 */
public record GetMedicationsByAssistedPersonQuery(
        UserId userId,
        PersonProfileId assistedPersonProfileId
) {
    public GetMedicationsByAssistedPersonQuery {
        Objects.requireNonNull(userId, "User id cannot be null");
        Objects.requireNonNull(assistedPersonProfileId, "Assisted person profile id cannot be null");
    }
}
