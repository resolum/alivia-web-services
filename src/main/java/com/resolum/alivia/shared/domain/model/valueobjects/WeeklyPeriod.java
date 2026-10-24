package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Set;
@Embeddable
public record WeeklyPeriod(Set<DayOfWeek> days, TimeRange range) {

    private static final Duration DAY = Duration.ofDays(1);
    private static final Duration WEEK = Duration.ofDays(7);

    public WeeklyPeriod {
        Objects.requireNonNull(days, "Days cannot be null.");
        Objects.requireNonNull(range, "Time range cannot be null.");

        if (days.isEmpty()) {
            throw new IllegalArgumentException(
                    "Days cannot be empty."
            );
        }

        days = Set.copyOf(days);
    }

    public boolean occursOn(LocalDate date) {
        Objects.requireNonNull(date, "Date cannot be null.");

        return days.contains(date.getDayOfWeek());
    }

    public boolean overlapsWith(WeeklyPeriod other) {
        Objects.requireNonNull(other, "Weekly period cannot be null.");

        for (DayOfWeek day : days) {
            Occurrence own = occurrenceStartingOn(day);

            for (DayOfWeek otherDay : other.days) {
                Occurrence theirs = other.occurrenceStartingOn(otherDay);

                if (own.overlapsAcrossWeeks(theirs)) {
                    return true;
                }
            }
        }

        return false;
    }

    private Occurrence occurrenceStartingOn(DayOfWeek day) {
        Duration start = DAY.multipliedBy(day.getValue() - 1)
                .plus(Duration.between(LocalTime.MIDNIGHT, range.start()));

        return new Occurrence(start, start.plus(range.duration()));
    }

    private record Occurrence(Duration start, Duration end) {

        private boolean overlapsAcrossWeeks(Occurrence other) {
            return overlaps(other)
                    || overlaps(other.shiftedBy(WEEK))
                    || overlaps(other.shiftedBy(WEEK.negated()));
        }

        private boolean overlaps(Occurrence other) {
            return start.compareTo(other.end) < 0
                    && other.start.compareTo(end) < 0;
        }

        private Occurrence shiftedBy(Duration offset) {
            return new Occurrence(start.plus(offset), end.plus(offset));
        }
    }

}