package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import com.resolum.alivia.subscriptions.domain.exceptions.InvalidMoney;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {
    public Money {
        if (amount == null || amount.signum() < 0 || !"PEN".equals(currency)) {
            throw new InvalidMoney("El monto debe ser no negativo y expresarse en PEN.");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public Money plus(Money other) {
        Objects.requireNonNull(other, "El monto adicional es obligatorio.");
        if (!currency.equals(other.currency)) {
            throw new InvalidMoney("No se pueden sumar montos de distintas monedas.");
        }
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(int quantity) {
        if (quantity < 0) {
            throw new InvalidMoney("La cantidad no puede ser negativa.");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }
}
