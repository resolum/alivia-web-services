package com.resolum.alivia.subscriptions.domain.model.valueobjects;

public record AssistedPersonId(Long value) {
    public AssistedPersonId {
        if (value == null || value <= 0) throw new IllegalArgumentException("AssistedPerson ID inválido.");
    }
}
