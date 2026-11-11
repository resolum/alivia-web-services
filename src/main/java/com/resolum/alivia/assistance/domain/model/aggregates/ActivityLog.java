package com.resolum.alivia.assistance.domain.model.aggregates;

import com.resolum.alivia.assistance.domain.exceptions.ActivityAssignmentNotActiveException;
import com.resolum.alivia.assistance.domain.exceptions.ActivityAssignmentNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.CareActivityNotActiveException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverNotActiveException;
import com.resolum.alivia.assistance.domain.exceptions.DayNotClosedYetException;
import com.resolum.alivia.assistance.domain.exceptions.NotResponsibleForActivityException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceEndedException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceNotScheduledException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceNotStartedException;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.assistance.domain.model.valueobjects.ActivityLogStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Historical snapshot of an assignment occurrence execution.
 */
@Entity
@Table(name = "assistance_activity_logs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_activity_log_assignment_date", columnNames = {"assignment_id", "date"})
})
@Getter
public class ActivityLog extends AuditableAbstractAggregateRoot<ActivityLog> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assisted_person_id", nullable = false)
    private AssistedPerson assistedPerson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "care_activity_id", nullable = false)
    private CareActivity careActivity;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 120)
    private String title;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "start", column = @Column(name = "start_time", nullable = false)),
            @AttributeOverride(name = "end", column = @Column(name = "end_time", nullable = false))
    })
    private TimeRange timeRange;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_caregiver_id")
    private Caregiver assignedCaregiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityLogStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_id")
    private Caregiver completedBy;

    private LocalDateTime completedAt;

    @Column(length = 500)
    private String notes;

    protected ActivityLog() {
    }

    private ActivityLog(CareActivity activity, ActivityAssignment assignment, LocalDate date,
                        ActivityLogStatus status, Caregiver completedBy, LocalDateTime completedAt, String notes) {
        this.assistedPerson = activity.getAssistedPerson();
        this.careActivity = activity;
        this.assignmentId = assignment.getId();
        this.date = date;
        this.title = activity.getTitle();
        this.timeRange = assignment.getSchedule().effectiveTimeRange();
        this.assignedCaregiver = assignment.getCaregiver();
        this.status = status;
        this.completedBy = completedBy;
        this.completedAt = completedAt;
        this.notes = notes == null || notes.isBlank() ? null : notes.strip();
    }

    public static ActivityLog completed(CareActivity activity, ActivityAssignment assignment, LocalDate date,
                                        Caregiver completedBy, LocalDateTime at, String notes) {
        validateCommon(activity, assignment, date);

        if (!activity.isActive()) {
            throw new CareActivityNotActiveException(activity.getId());
        }
        if (!assignment.isActive()) {
            throw new ActivityAssignmentNotActiveException(assignment.getId());
        }

        // Responsible check: if assigned to relative, completedBy must be null
        if (assignment.isAssignedToRelative()) {
            if (completedBy != null) {
                throw new NotResponsibleForActivityException("Activity was assigned to relative, but completedBy is a caregiver.");
            }
        } else {
            if (completedBy == null || !assignment.isAssignedTo(completedBy)) {
                throw new NotResponsibleForActivityException("Completed by caregiver is not the responsible assigned caregiver.");
            }
            if (!completedBy.isActive()) {
                throw new CaregiverNotActiveException(completedBy.getId());
            }
        }

        LocalDateTime start = assignment.getSchedule().occurrenceStart(date);
        LocalDateTime end = assignment.getSchedule().occurrenceEnd(date);
        if (at.isBefore(start)) {
            throw new OccurrenceNotStartedException(at, start);
        }
        if (at.isAfter(end)) {
            throw new OccurrenceEndedException(at, end);
        }

        return new ActivityLog(activity, assignment, date, ActivityLogStatus.COMPLETED, completedBy, at, notes);
    }

    public static ActivityLog cancelled(CareActivity activity, ActivityAssignment assignment, LocalDate date,
                                        LocalDateTime at) {
        validateCommon(activity, assignment, date);

        if (!activity.isActive()) {
            throw new CareActivityNotActiveException(activity.getId());
        }
        if (!assignment.isActive()) {
            throw new ActivityAssignmentNotActiveException(assignment.getId());
        }

        LocalDateTime end = assignment.getSchedule().occurrenceEnd(date);
        if (at.isAfter(end)) {
            throw new OccurrenceEndedException(at, end);
        }

        return new ActivityLog(activity, assignment, date, ActivityLogStatus.CANCELLED, null, null, null);
    }

    public static ActivityLog missed(CareActivity activity, ActivityAssignment assignment, LocalDate date,
                                     LocalDate today) {
        validateCommon(activity, assignment, date);

        LocalDate closingDate = assignment.getSchedule().closingDate(date);
        if (!closingDate.isBefore(today)) {
            throw new DayNotClosedYetException(closingDate, today);
        }

        return new ActivityLog(activity, assignment, date, ActivityLogStatus.MISSED, null, null, null);
    }

    private static void validateCommon(CareActivity activity, ActivityAssignment assignment, LocalDate date) {
        Objects.requireNonNull(activity, "Activity cannot be null.");
        Objects.requireNonNull(assignment, "Assignment cannot be null.");
        Objects.requireNonNull(date, "Date cannot be null.");

        if (assignment.getCareActivity() == null || !Objects.equals(assignment.getCareActivity().getId(), activity.getId())) {
            throw new ActivityAssignmentNotFoundException(assignment.getId());
        }
        if (!assignment.occursOn(date)) {
            throw new OccurrenceNotScheduledException(date);
        }
    }
}
