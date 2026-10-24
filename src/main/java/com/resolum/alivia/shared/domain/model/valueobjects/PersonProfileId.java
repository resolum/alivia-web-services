package com.resolum.alivia.shared.domain.model.valueobjects;

public record PersonProfileId(Long  personProfileId) {
    public PersonProfileId{

        if (personProfileId<=0){
            throw new IllegalArgumentException("person profile id cannot be null");
        }

    }
}
