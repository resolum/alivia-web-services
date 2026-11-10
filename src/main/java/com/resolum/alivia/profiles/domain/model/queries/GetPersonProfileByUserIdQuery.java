package com.resolum.alivia.profiles.domain.model.queries;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

public record GetPersonProfileByUserIdQuery(UserId userId) {
    public GetPersonProfileByUserIdQuery {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
    }
}
