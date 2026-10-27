package com.resolum.alivia.hcm.domain.model.aggregates;

import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleNotEditable;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkSchedulePeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleViewStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkShift;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "work_schedules", indexes = {
        @Index(name = "idx_work_schedule_employee_status", columnList = "employee_id,status"),
        @Index(name = "idx_work_schedule_business", columnList = "business_id")
}, uniqueConstraints = @UniqueConstraint(name = "uk_work_schedule_code", columnNames = "code"))
@Getter
public class WorkSchedule extends AuditableAbstractAggregateRoot<WorkSchedule> {
    private static final Comparator<WorkShift> SHIFT_ORDER =
            Comparator.comparing(WorkShift::day).thenComparing(WorkShift::startTime);

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;
    @Column(name = "business_id", nullable = false)
    private Long businessId;
    @Column(name = "contract_id", nullable = false)
    private Long contractId;
    @Column(length = 32, unique = true)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkScheduleStatus status;
    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "work_schedule_shifts", joinColumns = @JoinColumn(name = "work_schedule_id"))
    @OrderColumn(name = "shift_order")
    private List<WorkShift> shifts = new ArrayList<>();

    protected WorkSchedule() {
    }

    public WorkSchedule(Long employeeId, Long businessId, Long contractId, String name, WorkSchedulePeriod period,
                        List<WorkShift> shifts, Long actorUserId) {
        requirePositive(employeeId, "Employee identifier");
        requirePositive(businessId, "Business identifier");
        requirePositive(contractId, "Contract identifier");
        this.employeeId = employeeId;
        this.businessId = businessId;
        this.contractId = contractId;
        this.status = WorkScheduleStatus.DRAFT;
        apply(name, period, shifts, actorUserId);
    }

    public void assignCode(String code) {
        if (this.code != null || code == null || code.isBlank()) {
            throw new IllegalStateException("Work schedule code can only be assigned once.");
        }
        this.code = code;
    }

    public void update(String name, WorkSchedulePeriod period, List<WorkShift> shifts, Long actorUserId) {
        ensureEditable();
        apply(name, period, shifts, actorUserId);
        if (status == WorkScheduleStatus.ACTIVE) ensurePublishable();
    }

    /** Puts a draft into effect; the service first checks it against its contract and other schedules. */
    public void activate() {
        ensureEditable();
        ensurePublishable();
        this.status = WorkScheduleStatus.ACTIVE;
    }

    public void deactivate(Long actorUserId) {
        ensureEditable();
        this.status = WorkScheduleStatus.INACTIVE;
        this.lastModifiedBy = actorUserId;
    }

    public WorkSchedulePeriod getPeriod() {
        return new WorkSchedulePeriod(startDate, endDate);
    }

    public List<WorkShift> getShifts() {
        return List.copyOf(shifts);
    }

    public boolean isDraft() {
        return status == WorkScheduleStatus.DRAFT;
    }

    public BigDecimal weeklyHours() {
        long minutes = shifts.stream().map(WorkShift::duration).mapToLong(Duration::toMinutes).sum();
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    public Set<DayOfWeek> workingDays() {
        var days = EnumSet.noneOf(DayOfWeek.class);
        shifts.forEach(shift -> days.add(shift.day()));
        return days;
    }

    /** True when one shift of that day contains the whole range (used to assign technician visits). */
    public boolean covers(DayOfWeek day, TimeRange range) {
        return shifts.stream().anyMatch(shift -> shift.day() == day && shift.range().contains(range));
    }

    public boolean isInEffectOn(LocalDate date) {
        return status == WorkScheduleStatus.ACTIVE && getPeriod().contains(date);
    }

    public WorkScheduleViewStatus viewStatus(LocalDate today) {
        if (status == WorkScheduleStatus.DRAFT) return WorkScheduleViewStatus.DRAFT;
        if (status == WorkScheduleStatus.INACTIVE) return WorkScheduleViewStatus.INACTIVE;
        if (startDate.isAfter(today)) return WorkScheduleViewStatus.SCHEDULED;
        if (endDate != null && endDate.isBefore(today)) return WorkScheduleViewStatus.FINISHED;
        return WorkScheduleViewStatus.ACTIVE;
    }

    private void apply(String name, WorkSchedulePeriod period, List<WorkShift> shifts, Long actorUserId) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new IllegalArgumentException("Work schedule name is required and cannot exceed 100 characters.");
        }
        if (period == null) throw new IllegalArgumentException("Work schedule period is required.");
        var ordered = shifts == null ? List.<WorkShift>of() : shifts.stream().sorted(SHIFT_ORDER).toList();
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i - 1).overlapsWith(ordered.get(i))) {
                throw new IllegalArgumentException("Work shifts of the same day cannot overlap.");
            }
        }
        this.name = name.trim();
        this.startDate = period.startDate();
        this.endDate = period.endDate();
        this.shifts.clear();
        this.shifts.addAll(ordered);
        this.lastModifiedBy = actorUserId;
    }

    private void ensurePublishable() {
        if (shifts.isEmpty()) {
            throw new IllegalArgumentException("A work schedule needs at least one work shift.");
        }
    }

    private void ensureEditable() {
        if (status == WorkScheduleStatus.INACTIVE) throw new WorkScheduleNotEditable();
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) throw new IllegalArgumentException(name + " must be positive.");
    }
}
