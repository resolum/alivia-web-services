package com.resolum.alivia.nursing.domain.model.valueobjects;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * When an assignment happens: once on a date, or weekly on some days.
 *
 * <p>A time range that crosses midnight belongs to the date it starts on, so Monday 22:00-06:00
 * ends on Tuesday at 06:00. The constructor only validates the shape; "not in the past" is
 * checked by {@link com.resolum.alivia.nursing.domain.model.aggregates.CareActivity}.</p>
 *
 * @param type         the schedule type
 * @param date         the date of a one-time schedule, {@code null} when weekly
 * @param timeRange    the time range of a one-time schedule, {@code null} when weekly
 * @param weeklyPeriod the days and time range of a weekly schedule, {@code null} when one-time
 */
public record Schedule(ScheduleType type, LocalDate date, TimeRange timeRange, WeeklyPeriod weeklyPeriod) {

    /**
     * Validates the schedule.
     *
     * @throws IllegalArgumentException when the fields do not match the type
     */
    public Schedule {
        Objects.requireNonNull(type, "Schedule type cannot be null.");
        boolean valid = type == ScheduleType.ONE_TIME
                ? date != null && timeRange != null && weeklyPeriod == null
                : weeklyPeriod != null && date == null && timeRange == null;
        if (!valid) {
            throw new IllegalArgumentException("A one-time schedule needs a date and a time range; "
                    + "a weekly schedule needs only a weekly period.");
        }
    }

    /**
     * Creates a one-time schedule.
     *
     * @param date      the date the occurrence starts on
     * @param timeRange the time range
     * @return the schedule
     */
    public static Schedule oneTime(LocalDate date, TimeRange timeRange) {
        return new Schedule(ScheduleType.ONE_TIME, date, timeRange, null);
    }

    /**
     * Creates a weekly schedule.
     *
     * @param weeklyPeriod the days and time range
     * @return the schedule
     */
    public static Schedule weekly(WeeklyPeriod weeklyPeriod) {
        return new Schedule(ScheduleType.WEEKLY, null, null, weeklyPeriod);
    }

    /**
     * Indicates whether an occurrence starts on the given date.
     *
     * @param date the date
     * @return {@code true} when an occurrence starts that day
     */
    public boolean occursOn(LocalDate date) {
        return type == ScheduleType.ONE_TIME ? this.date.equals(date) : weeklyPeriod.occursOn(date);
    }

    /**
     * Returns the time range of every occurrence.
     *
     * @return the time range
     */
    public TimeRange effectiveTimeRange() {
        return type == ScheduleType.ONE_TIME ? timeRange : weeklyPeriod.range();
    }

    /**
     * Returns the actual start of the occurrence that starts on the given date.
     *
     * @param date the occurrence date
     * @return the actual start
     */
    public LocalDateTime occurrenceStart(LocalDate date) {
        return date.atTime(effectiveTimeRange().start());
    }

    /**
     * Returns the actual end of the occurrence that starts on the given date.
     *
     * @param date the occurrence date
     * @return the actual end, on the next day when the range crosses midnight
     */
    public LocalDateTime occurrenceEnd(LocalDate date) {
        var range = effectiveTimeRange();
        return (range.crossesMidnight() ? date.plusDays(1) : date).atTime(range.end());
    }
}
