package com.resolum.alivia.business.domain.model.queries;

public record GetAdministratorByIdQuery(Long administratorId) {
    public GetAdministratorByIdQuery {
        if (administratorId == null || administratorId <= 0) {
            throw new IllegalArgumentException("Administrator ID must be a positive number");
        }
    }
}
