package com.resolum.alivia.analytics.domain.model.valueobjects;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The commercial and operating indicators of the whole service in a period. A section whose source could not be read
 * is {@code null} and its name is in {@code incompleteSources}; it is never answered as zeros.
 *
 * @param subscriptionsByStatus     every subscription by status, now (not limited to the period)
 * @param alertsBySeverity          alerts of every home in the period, by severity
 * @param devicesByConnectionStatus last known state of every device that reported, now
 * @param incompleteSources         some of {@code sales}, {@code subscriptions}, {@code installations},
 *                                  {@code alerts}, {@code devices}; empty when the overview is whole
 */
public record BusinessOverview(Instant from, Instant to, Instant computedAt, SalesFigures commercial,
                               Map<String, Long> subscriptionsByStatus, Installations installations,
                               Map<String, Long> alertsBySeverity, Map<String, Long> devicesByConnectionStatus,
                               List<String> incompleteSources) {

    /**
     * @param total    homes whose installation was approved, ever
     * @param inPeriod those approved in the period
     */
    public record Installations(long total, long inPeriod) {
    }

    public boolean complete() {
        return incompleteSources.isEmpty();
    }
}
