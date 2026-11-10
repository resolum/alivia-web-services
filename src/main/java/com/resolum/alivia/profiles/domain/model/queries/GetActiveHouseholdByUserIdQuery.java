package com.resolum.alivia.profiles.domain.model.queries;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.Objects;

/**
 * Query to find the active household for a given user.
 */
public record GetActiveHouseholdByUserIdQuery(UserId userId) {
    public GetActiveHouseholdByUserIdQuery {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
