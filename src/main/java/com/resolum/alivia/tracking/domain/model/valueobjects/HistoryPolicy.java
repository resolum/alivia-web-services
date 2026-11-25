package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.time.Duration;

/**
 * The limits of the history a user may read. They are product decisions (D-TR-9) and come from configuration, so
 * changing them does not change the code.
 *
 * @param maxRange        the longest period one request may ask for
 * @param defaultPageSize the size of a page when the request does not say
 * @param maxPageSize     the largest page a request may ask for
 */
public record HistoryPolicy(Duration maxRange, int defaultPageSize, int maxPageSize) {

    public HistoryPolicy {
        if (maxRange == null || maxRange.isNegative() || maxRange.isZero()) {
            throw new IllegalArgumentException("The longest period must be positive.");
        }
        if (maxPageSize < 1 || defaultPageSize < 1 || defaultPageSize > maxPageSize) {
            throw new IllegalArgumentException("The page sizes must be positive and the default no larger than the maximum.");
        }
    }

    /** The values proposed in the plan: 31 days, 20 events per page and at most 100. */
    public static HistoryPolicy proposed() {
        return new HistoryPolicy(Duration.ofDays(31), 20, 100);
    }
}
