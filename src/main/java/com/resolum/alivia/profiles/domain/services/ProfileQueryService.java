package com.resolum.alivia.profiles.domain.services;

import com.resolum.alivia.profiles.domain.model.queries.ExistsProfileByIdQuery;

/**
 * Domain query service for generic profile operations.
 */
public interface ProfileQueryService {

    /**
     * Checks whether a profile exists by its identifier.
     *
     * @param query the exists profile by id query
     * @return {@code true} if the profile exists; {@code false} otherwise
     */
    boolean handle(ExistsProfileByIdQuery query);
}
