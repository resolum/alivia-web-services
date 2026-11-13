package com.resolum.alivia.assistance.domain.model.valueobjects;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CareActivityType {
    HYGIENE,
    MEDICATION,
    APPOINTMENT,
    THERAPY,
    MEAL,
    BATH,
    EXERCISE,
    OTHER;

    @JsonCreator
    public static CareActivityType fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "NUTRITION", "HYDRATION", "MEAL" -> MEAL;
            case "MOBILITY", "EXERCISE" -> EXERCISE;
            case "THERAPY" -> THERAPY;
            case "BATH" -> BATH;
            case "HYGIENE" -> HYGIENE;
            case "MEDICATION" -> MEDICATION;
            case "SOCIAL", "APPOINTMENT" -> APPOINTMENT;
            default -> {
                try {
                    yield CareActivityType.valueOf(normalized);
                } catch (IllegalArgumentException e) {
                    yield OTHER;
                }
            }
        };
    }
}
