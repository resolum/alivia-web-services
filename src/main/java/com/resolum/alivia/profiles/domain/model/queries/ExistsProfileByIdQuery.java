package com.resolum.alivia.profiles.domain.model.queries;

/**
 * Query to check whether any profile (person, staff, or business) exists by its identifier.
 *
 * @param profileId the internal profile identifier
 */
public record ExistsProfileByIdQuery(Long profileId) {

    public ExistsProfileByIdQuery {
        if (profileId == null || profileId <= 0) {
            throw new IllegalArgumentException("Profile ID must be greater than 0");
        }
    }
}
