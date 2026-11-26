package com.resolum.alivia.tracking.domain.model.valueobjects;

import com.resolum.alivia.tracking.domain.exceptions.InvalidHistoryQuery;

import java.time.Duration;
import java.time.Instant;

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

    /**
     * Checks what a request asked for and fills in what it left out: no end means {@code now}, no start means the longest
     * period back from the end, no page means the first and no size the default.
     *
     * @throws InvalidHistoryQuery {@code invalid_page}, {@code invalid_page_size}, {@code invalid_range} (the start is
     *         after the end) or {@code range_too_large}
     */
    public HistoryWindow window(Instant from, Instant to, Integer page, Integer size, Instant now) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? defaultPageSize : size;
        if (pageNumber < 0) {
            throw new InvalidHistoryQuery("invalid_page", "The page must be 0 or more.");
        }
        if (pageSize < 1 || pageSize > maxPageSize) {
            throw new InvalidHistoryQuery("invalid_page_size", "The size must be between 1 and " + maxPageSize + ".");
        }
        var end = to == null ? now : to;
        var start = from == null ? end.minus(maxRange) : from;
        if (start.isAfter(end)) {
            throw new InvalidHistoryQuery("invalid_range", "The start of the period cannot be after its end.");
        }
        if (Duration.between(start, end).compareTo(maxRange) > 0) {
            throw new InvalidHistoryQuery("range_too_large",
                    "The period cannot be longer than " + maxRange.toDays() + " days.");
        }
        return new HistoryWindow(start, end, pageNumber, pageSize);
    }

    /** The values proposed in the plan: 31 days, 20 events per page and at most 100. */
    public static HistoryPolicy proposed() {
        return new HistoryPolicy(Duration.ofDays(31), 20, 100);
    }
}
