package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorType;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;

import java.math.BigDecimal;
import java.util.Map;

public record PlanQuoteResource(
        PlanCode planCode,
        String currency,
        String billingPeriod,
        int rooms,
        Map<ActuatorType, Integer> deviceQuantities,
        BigDecimal baseMonthlyAmount,
        BigDecimal additionalRoomsMonthlyAmount,
        BigDecimal devicesMonthlyAmount,
        BigDecimal totalMonthlyAmount
) {
}
