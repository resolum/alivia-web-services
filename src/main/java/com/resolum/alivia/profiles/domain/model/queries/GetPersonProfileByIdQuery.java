package com.resolum.alivia.profiles.domain.model.queries;

public record GetPersonProfileByIdQuery(Long profileId) {
    public GetPersonProfileByIdQuery {
        if (profileId == null || profileId <= 0) {
            throw new IllegalArgumentException("Profile ID must be greater than 0");
        }
    }
}
