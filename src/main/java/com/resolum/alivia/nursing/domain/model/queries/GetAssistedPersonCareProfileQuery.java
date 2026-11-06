package com.resolum.alivia.nursing.domain.model.queries;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.Objects;

/**
 * Query for the care profile registered by a responsible client.
 *
 * @param requesterUserId the responsible client
 */
public record GetAssistedPersonCareProfileQuery(UserId requesterUserId) {

    /**
     * Validates the query.
     */
    public GetAssistedPersonCareProfileQuery {
        Objects.requireNonNull(requesterUserId, "Requester cannot be null.");
    }
}
