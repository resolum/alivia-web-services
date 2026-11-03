package com.resolum.alivia.profiles.domain.services;

import java.util.Optional;

import com.resolum.alivia.profiles.domain.model.aggregates.Profile;
import com.resolum.alivia.profiles.domain.model.queries.GetOwnProfileQuery;

/**
 * Domain query service for the profile owned by an authenticated user.
 */
public interface OwnProfileQueryService {

    /**
     * Finds the person or staff profile linked to the user.
     *
     * @param query the get own profile query
     * @return the profile, or empty when the user has no linked profile
     */
    Optional<Profile> handle(GetOwnProfileQuery query);
}
