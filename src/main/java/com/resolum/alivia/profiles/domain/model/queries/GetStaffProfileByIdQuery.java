package com.resolum.alivia.profiles.domain.model.queries;

public record GetStaffProfileByIdQuery(Long profileId) {
    public GetStaffProfileByIdQuery {
        if (profileId == null || profileId <= 0) {
            throw new IllegalArgumentException("Profile ID must be greater than 0");
        }
    }
}
