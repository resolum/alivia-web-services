package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record PersonProfileId(Long  personProfileId) {
    public PersonProfileId{

        if (personProfileId==null||personProfileId<=0){
            throw new IllegalArgumentException("person profile id cannot be null");
        }

    }
}
