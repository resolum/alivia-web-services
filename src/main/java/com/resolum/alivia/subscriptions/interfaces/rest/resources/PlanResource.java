package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorType;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PriceQualifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PlanResource(
        Long id,
        PlanCode code,
        String name,
        String description,
        PlanStatus status,
        String currency,
        String billingPeriod,
        BigDecimal basePriceAmount,
        BigDecimal minimumMonthlyAmount,
        PriceQualifier priceQualifier,
        BigDecimal additionalRoomMonthlyAmount,
        int minimumRooms,
        int minActuators,
        int maxActuators,
        int includedActuators,
        Map<ActuatorType, BigDecimal> deviceMonthlyPrices,
        List<String> features,
        List<String> includedDevices
) {
}
