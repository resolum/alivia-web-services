package com.resolum.alivia.analytics.domain.model.valueobjects;

import java.time.Duration;
import java.time.Instant;

/**
 * The period of a query: {@code from} included, {@code to} excluded.
 */
public record AnalyticsPeriod(Instant from, Instant to) {

    public static final Duration DEFAULT_LENGTH = Duration.ofDays(30);
    /** shortcut: a fixed limit (decision D-RM-5 is still open), move it to configuration when it is decided. */
    public static final Duration MAX_LENGTH = Duration.ofDays(366);

    public AnalyticsPeriod {
        if (from == null || to == null) throw new IllegalArgumentException("The period needs a start and an end.");
        if (from.isAfter(to)) throw new IllegalArgumentException("The start of the period is after its end.");
        if (Duration.between(from, to).compareTo(MAX_LENGTH) > 0) {
            throw new IllegalArgumentException("The period is longer than " + MAX_LENGTH.toDays() + " days.");
        }
    }

    /** Without {@code to} it ends now; without {@code from} it is the {@link #DEFAULT_LENGTH} before its end. */
    public static AnalyticsPeriod of(Instant from, Instant to, Instant now) {
        var end = to == null ? now : to;
        return new AnalyticsPeriod(from == null ? end.minus(DEFAULT_LENGTH) : from, end);
    }

    /** The period of the same length that ends where this one starts. */
    public AnalyticsPeriod previous() {
        return new AnalyticsPeriod(from.minus(Duration.between(from, to)), from);
    }

    public boolean contains(Instant instant) {
        return instant != null && !instant.isBefore(from) && instant.isBefore(to);
    }
}
