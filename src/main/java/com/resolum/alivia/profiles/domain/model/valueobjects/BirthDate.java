package com.resolum.alivia.profiles.domain.model.valueobjects;

import java.time.LocalDate;

import jakarta.persistence.Embeddable;

@Embeddable
public record BirthDate(LocalDate date) {
    public BirthDate {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
    }
}
