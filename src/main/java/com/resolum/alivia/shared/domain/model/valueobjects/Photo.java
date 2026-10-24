package com.resolum.alivia.shared.domain.model.valueobjects;

public record Photo(String photoUrl, String photoPublicId) {

    public Photo {
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new IllegalArgumentException("Photo URL cannot be null or blank");
        }
        if (photoPublicId == null || photoPublicId.isBlank()) {
            throw new IllegalArgumentException("Photo public ID cannot be null or blank");
        }
    }
}
