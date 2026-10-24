package com.resolum.alivia.profiles.domain.model.valueobjects;

import java.time.LocalDate;

public record BirthDate(LocalDate date) {
    public BirthDate {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
    }
}
