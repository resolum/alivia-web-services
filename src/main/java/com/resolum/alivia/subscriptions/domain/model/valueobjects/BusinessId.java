package com.resolum.alivia.subscriptions.domain.model.valueobjects;

public record BusinessId(Long value) {
    public BusinessId {
        if (value == null || value <= 0) throw new IllegalArgumentException("Business ID inválido.");
    }
}
