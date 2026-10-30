package com.resolum.alivia.shared.domain.model.valueobjects;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Embeddable;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

@Embeddable
@Schema(description = "Rango de horas (hora de inicio y hora de fin)")
public record TimeRange(
        @Schema(description = "Hora de inicio en formato HH:mm:ss", example = "08:00:00")
        LocalTime start,

        @Schema(description = "Hora de fin en formato HH:mm:ss", example = "18:00:00")
        LocalTime end) {

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