package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import java.time.LocalDateTime;

public record BillingPeriod(LocalDateTime start, LocalDateTime end) {
    public BillingPeriod {
        if (start == null || end == null || !end.isAfter(start))
            throw new IllegalArgumentException("El período de facturación es inválido.");
    }
}
