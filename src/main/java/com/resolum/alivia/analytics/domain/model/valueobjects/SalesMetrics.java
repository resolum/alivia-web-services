package com.resolum.alivia.analytics.domain.model.valueobjects;

import java.time.Instant;
import java.util.List;

/**
 * The sales of a period.
 *
 * @param planId         the plan it is limited to, or {@code null} for every plan
 * @param hasResults     {@code false} when there was no hiring for the filters: the figures are zeros and the lists
 *                       empty
 * @param byBillingCycle the breakdown by periodicity ({@code MONTHLY}, {@code ANNUAL})
 * @param previous       the same figures for the period of the same length just before, to compare
 */
public record SalesMetrics(Instant from, Instant to, Long planId, Instant computedAt, boolean hasResults,
                           SalesFigures total, List<PlanSales> byPlan, List<CycleSales> byBillingCycle,
                           PreviousPeriod previous) {

    public record PlanSales(Long planId, String planCode, String planName, SalesFigures figures) {
    }

    public record CycleSales(String billingCycle, SalesFigures figures) {
    }

    public record PreviousPeriod(Instant from, Instant to, SalesFigures figures) {
    }
}
