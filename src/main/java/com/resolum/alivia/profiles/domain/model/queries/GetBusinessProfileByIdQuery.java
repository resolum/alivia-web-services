package com.resolum.alivia.profiles.domain.model.queries;

public record GetBusinessProfileByIdQuery(Long profileId) {
    public GetBusinessProfileByIdQuery {
        if (profileId == null || profileId <= 0) {
            throw new IllegalArgumentException("Profile ID must be greater than 0");
        }
    }
}
