package com.resolum.alivia.business.domain.model.queries;

public record GetAdministratorByUserIdQuery(Long userId) {
    public GetAdministratorByUserIdQuery {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID must be a positive number");
        }
    }
}
