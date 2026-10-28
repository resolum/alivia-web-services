package com.resolum.alivia.business.domain.model.queries;

public record GetBusinessByIdQuery(Long businessId) {
    public GetBusinessByIdQuery {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("Business ID must be a positive number");
        }
    }
}
