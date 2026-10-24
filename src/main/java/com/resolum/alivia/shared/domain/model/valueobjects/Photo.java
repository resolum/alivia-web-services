package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record Photo(String photoUrl, String photoPublicId)
{

    public Photo {
        if (photoUrl==null||photoUrl.isBlank()){
        throw new IllegalArgumentException("Photo url cannot be null or blank.");
        }

        if (photoPublicId==null || photoPublicId.isBlank()){
            throw new IllegalArgumentException("Photo public id cannot be null or blank.");
        }
    }
}
