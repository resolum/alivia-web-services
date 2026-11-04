package com.resolum.alivia.business.domain.model.queries;

public record GetBusinessByAdministratorIdQuery(Long administratorId) {
    public GetBusinessByAdministratorIdQuery {
        if (administratorId == null || administratorId <= 0) {
            throw new IllegalArgumentException("Administrator ID must be a positive number");
        }
    }
}
