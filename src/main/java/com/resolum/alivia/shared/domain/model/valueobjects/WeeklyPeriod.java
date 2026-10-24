package com.resolum.alivia.shared.domain.model.valueobjects;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WeeklyPeriod(DayOfWeek dayOfWeek, LocalTime openingTime, LocalTime closingTime) {

    public WeeklyPeriod {
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("Day of week cannot be null");
        }
        if (openingTime == null || closingTime == null) {
            throw new IllegalArgumentException("Opening time and closing time cannot be null");
        }
    }

    public boolean occursOn(DayOfWeek dayOfWeek) {
        return this.dayOfWeek == dayOfWeek;
    }

    public boolean overlapsWith(WeeklyPeriod other) {
        return this.dayOfWeek == other.dayOfWeek && this.openingTime.isBefore(other.closingTime)
                && this.closingTime.isAfter(other.openingTime);
    }
}
