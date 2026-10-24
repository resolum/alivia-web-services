package com.resolum.alivia.subscriptions.domain.model.valueobjects;

public record HouseholdId(String value) {
    public HouseholdId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Se requiere un Household ID.");
        value = value.trim();
    }
}
