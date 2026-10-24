package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record UserId(Long userId) {
    public UserId{
        if(userId==null||userId<=0) {
            throw new IllegalArgumentException("user id cannot be null or less than or equal to zero");
        }
    }
}
