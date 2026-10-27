package com.resolum.alivia.hcm.domain.model.valueobjects;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

@Embeddable
public record WorkShift(
        @Enumerated(EnumType.STRING) @Column(name = "day_of_week", nullable = false, length = 10) DayOfWeek day,
        @Column(name = "start_time", nullable = false) LocalTime startTime,
        @Column(name = "end_time", nullable = false) LocalTime endTime) {

    public WorkShift {
        Objects.requireNonNull(day, "Shift day is required.");
        var range = new TimeRange(startTime, endTime);
        if (range.crossesMidnight()) {
            throw new IllegalArgumentException("A work shift cannot cross midnight.");
        }
    }

    public TimeRange range() {
        return new TimeRange(startTime, endTime);
    }

    public Duration duration() {
        return range().duration();
    }

    public boolean overlapsWith(WorkShift other) {
        return day == other.day && range().overlaps(other.range());
    }
}
