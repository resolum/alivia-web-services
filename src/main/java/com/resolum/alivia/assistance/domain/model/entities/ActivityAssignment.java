package com.resolum.alivia.assistance.domain.model.entities;

import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.valueobjects.ActivityStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.Schedule;
import com.resolum.alivia.assistance.domain.model.valueobjects.ScheduleType;
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
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Assignment of a care activity: who does it and when.
 * Internal entity of CareActivity.
 */
@Entity
@Table(name = "assistance_activity_assignments")
@Getter
public class ActivityAssignment extends AuditableModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "care_activity_id", nullable = false)
    private CareActivity careActivity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caregiver_id")
    private Caregiver caregiver;

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

    public ActivityAssignment(CareActivity careActivity, Caregiver caregiver, Schedule schedule) {
        this.careActivity = Objects.requireNonNull(careActivity, "Care activity cannot be null.");
        this.caregiver = caregiver;
        applySchedule(schedule);
        this.status = ActivityStatus.ACTIVE;
    }

    public void update(Caregiver caregiver, Schedule schedule) {
        this.caregiver = caregiver;
        if (schedule != null) {
            applySchedule(schedule);
        }
    }

    public void activate() {
        this.status = ActivityStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ActivityStatus.INACTIVE;
    }

    public Schedule getSchedule() {
        var range = new TimeRange(startTime, endTime);
        if (scheduleType == ScheduleType.ONE_TIME) {
            return Schedule.oneTime(scheduledDate, range);
        }
        var days = Arrays.stream(weekDays.split(","))
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
        return Schedule.weekly(new WeeklyPeriod(days, range));
    }

    public boolean occursOn(LocalDate date) {
        return isActive() && getSchedule().occursOn(date);
    }

    public boolean isAssignedTo(Caregiver caregiver) {
        if (caregiver == null || this.caregiver == null) {
            return false;
        }
        return Objects.equals(this.caregiver.getId(), caregiver.getId());
    }

    public boolean isAssignedToRelative() {
        return caregiver == null;
    }

    public boolean isActive() {
        return status == ActivityStatus.ACTIVE;
    }

    public Long getCaregiverId() {
        return caregiver != null ? caregiver.getId() : null;
    }

    public boolean isRecurring() {
        return scheduleType == ScheduleType.WEEKLY;
    }

    private void applySchedule(Schedule schedule) {
        Objects.requireNonNull(schedule, "Schedule cannot be null.");
        this.scheduleType = schedule.type();
        this.scheduledDate = schedule.date();
        if (schedule.weeklyPeriod() != null) {
            this.weekDays = schedule.weeklyPeriod().days().stream()
                    .sorted()
                    .map(DayOfWeek::name)
                    .collect(Collectors.joining(","));
        } else {
            this.weekDays = null;
        }
        this.startTime = schedule.effectiveTimeRange().start();
        this.endTime = schedule.effectiveTimeRange().end();
    }
}
