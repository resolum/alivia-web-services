package com.resolum.alivia.profiles.domain.model.queries;

/**
 * Query that asks for the profile owned by an authenticated user.
 *
 * @param userId the authenticated user identifier
 */
public record GetOwnProfileQuery(Long userId) {

    public GetOwnProfileQuery {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User id must be greater than 0");
        }
    }
}
