package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;
@Embeddable
public record TimeRange(LocalTime start, LocalTime end) {

    private static final Duration DAY = Duration.ofDays(1);

    public TimeRange {
        Objects.requireNonNull(start, "Start time cannot be null.");
        Objects.requireNonNull(end, "End time cannot be null.");

        if (start.equals(end)) {
            throw new IllegalArgumentException(
                    "Start and end times cannot be the same."
            );
        }
    }

    public boolean crossesMidnight() {
        return end.isBefore(start);
    }

    public boolean contains(LocalTime time) {
        Objects.requireNonNull(time, "Time cannot be null.");

        if (crossesMidnight()) {
            return !time.isBefore(start) || time.isBefore(end);
        }

        return !time.isBefore(start) && time.isBefore(end);
    }

    public boolean contains(TimeRange other) {
        Objects.requireNonNull(other, "Time range cannot be null.");

        return other.startOffset().compareTo(startOffset()) >= 0
                && other.endOffset().compareTo(endOffset()) <= 0;
    }

    public boolean overlaps(TimeRange other) {
        Objects.requireNonNull(other, "Time range cannot be null.");

        return startOffset().compareTo(other.endOffset()) < 0
                && other.startOffset().compareTo(endOffset()) < 0;
    }

    public Duration duration() {
        Duration duration = Duration.between(start, end);

        return crossesMidnight() ? duration.plus(DAY) : duration;
    }

    private Duration startOffset() {
        return Duration.between(LocalTime.MIDNIGHT, start);
    }

    private Duration endOffset() {
        return startOffset().plus(duration());
    }

}