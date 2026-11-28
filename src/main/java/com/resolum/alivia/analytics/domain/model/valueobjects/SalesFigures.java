package com.resolum.alivia.analytics.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @param hirings           contracting requests whose payment was authorized in the period
 * @param subscriptionsSold those of them whose payment was captured
 * @param revenue           the monthly amount of the ones captured, by currency; empty when there are none
 */
public record SalesFigures(long hirings, long subscriptionsSold, Map<String, BigDecimal> revenue) {

    public static final SalesFigures NONE = new SalesFigures(0, 0, Map.of());

    public SalesFigures {
        revenue = Map.copyOf(revenue);
    }
}
