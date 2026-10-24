package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record BusinessId(Long businessId) {

    public BusinessId{
        if (businessId ==null|| businessId<=0){
            throw  new IllegalArgumentException("Business ID must be a positive number.");
        }

    }

}
