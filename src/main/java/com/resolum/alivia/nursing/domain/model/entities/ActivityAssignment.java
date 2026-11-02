package com.resolum.alivia.nursing.domain.model.entities;

import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.valueobjects.ActivityStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.Schedule;
import com.resolum.alivia.nursing.domain.model.valueobjects.ScheduleType;
import com.resolum.alivia.shared.domain.model.entities.AuditableModel;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Assignment (shift) of a care activity: who does it and when.
 *
 * <p>It lives inside {@link CareActivity} and is only reached through its root. A {@code null}
 * caregiver means the relative does the activity. The caregiver is the identifier of the
 * caregiver membership of the assisted person.</p>
 *
 * <p>The schedule is persisted as primitive columns and rebuilt by {@link #getSchedule()}.</p>
 */
@Entity
@Getter
public class ActivityAssignment extends AuditableModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "care_activity_id", nullable = false)
    private CareActivity careActivity;

    private Long caregiverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleType scheduleType;

    private LocalDate scheduledDate;

    @Column(length = 80)
    private String weekDays;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityStatus status;

    protected ActivityAssignment() {
    }

    /**
     * Creates an active assignment.
     *
     * @param careActivity the owning activity
     * @param caregiverId  the caregiver, or {@code null} when the relative does it
     * @param schedule     the schedule
     */
    public ActivityAssignment(CareActivity careActivity, Long caregiverId, Schedule schedule) {
        this.careActivity = Objects.requireNonNull(careActivity, "Care activity cannot be null.");
        this.caregiverId = caregiverId;
        Objects.requireNonNull(schedule, "Schedule cannot be null.");
        this.scheduleType = schedule.type();
        this.scheduledDate = schedule.date();
        if (schedule.weeklyPeriod() != null) {
            this.weekDays = schedule.weeklyPeriod().days().stream().sorted().map(DayOfWeek::name)
                    .collect(Collectors.joining(","));
        }
        this.startTime = schedule.effectiveTimeRange().start();
        this.endTime = schedule.effectiveTimeRange().end();
        this.status = ActivityStatus.ACTIVE;
    }

    public Schedule getSchedule() {
        var range = new TimeRange(startTime, endTime);
        if (scheduleType == ScheduleType.ONE_TIME) {
            return Schedule.oneTime(scheduledDate, range);
        }
        var days = Arrays.stream(weekDays.split(",")).map(DayOfWeek::valueOf).collect(Collectors.toSet());
        return Schedule.weekly(new WeeklyPeriod(days, range));
    }

    public boolean occursOn(LocalDate date) {
        return getSchedule().occursOn(date);
    }

    public boolean isAssignedTo(Long caregiverId) {
        return this.caregiverId != null && this.caregiverId.equals(caregiverId);
    }

    public boolean isAssignedToRelative() {
        return caregiverId == null;
    }

    public boolean isActive() {
        return status == ActivityStatus.ACTIVE;
    }

    public boolean isRecurring() {
        return scheduleType == ScheduleType.WEEKLY;
    }
}
