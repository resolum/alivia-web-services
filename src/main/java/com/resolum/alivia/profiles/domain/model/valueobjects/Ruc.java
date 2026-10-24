package com.resolum.alivia.profiles.domain.model.valueobjects;

public record Ruc(String ruc) {
    public Ruc {
        if (ruc == null || !ruc.matches("\\d{11}")) {
            throw new IllegalArgumentException("RUC must be 11 digits");
        }
    }
}
