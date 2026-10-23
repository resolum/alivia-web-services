package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Map;

public record PlanQuote(
        PlanCode planCode,
        String currency,
        int rooms,
        Map<ActuatorType, Integer> deviceQuantities,
        BigDecimal baseMonthlyAmount,
        BigDecimal additionalRoomsMonthlyAmount,
        BigDecimal devicesMonthlyAmount,
        BigDecimal totalMonthlyAmount
) {
}
