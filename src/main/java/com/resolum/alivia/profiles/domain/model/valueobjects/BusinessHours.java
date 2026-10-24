package com.resolum.alivia.profiles.domain.model.valueobjects;

import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;

import java.time.LocalDateTime;
import java.util.List;

public record BusinessHours(List<WeeklyPeriod> periods) {
    public BusinessHours(List<WeeklyPeriod> periods) {
        if (periods == null || periods.isEmpty()) {
            throw new IllegalArgumentException("Periods cannot be null or empty");
        }
        this.periods = periods;
    }

    public boolean isOpenAt(LocalDateTime dateTime) {
        return periods.stream()
                .filter(period -> period.dayOfWeek() == dateTime.getDayOfWeek())
                .anyMatch(period -> dateTime.toLocalTime().isAfter(period.openingTime())
                        && dateTime.toLocalTime().isBefore(period.closingTime()));
    }
}
