package com.resolum.alivia.nursing.domain.model.aggregates;

import com.resolum.alivia.nursing.domain.model.commands.RecordMedicationOutcomeCommand;
import com.resolum.alivia.nursing.domain.model.valueobjects.ActivityLogStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministration;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationResult;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * What happened to one occurrence of an assignment, identified by the assignment and the date
 * the occurrence starts on.
 *
 * <p>The log is immutable: it is created once and never updated or deleted. The unique
 * constraint on assignment and date rejects a second log for the same occurrence, also under
 * concurrent requests. Title, time range and assigned caregiver are a copy of what was in
 * force that day. A {@code null} completedBy means the relative recorded it.</p>
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_activity_logs_assignment_date",
        columnNames = {"assignment_id", "occurrence_date"}))
@Getter
public class ActivityLog extends AuditableAbstractAggregateRoot<ActivityLog> {

    @Column(nullable = false)
    private Long assistedPersonProfileId;

    @Column(nullable = false)
    private Long careActivityId;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    private Long assignedCaregiverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityLogStatus status;

    private Long completedBy;

    private LocalDateTime completedAt;

    @Column(length = RecordMedicationOutcomeCommand.MAX_TEXT_LENGTH)
    private String notes;

    // Medication outcome fields.
    private LocalDateTime registeredAt;

    @Column(nullable = false)
    private boolean late;

    @Column(length = RecordMedicationOutcomeCommand.MAX_TEXT_LENGTH)
    private String omissionReason;

    protected ActivityLog() {
    }

    /**
     * Records the outcome of a medication administration as {@code COMPLETED} or {@code OMITTED}.
     *
     * <p>A moment after the end of the occurrence is accepted and marked as late.</p>
     *
     * @param administration the administration, as scheduled that day
     * @param command        the record command
     * @param completedBy    the caregiver who records it, or {@code null} when a relative records it
     * @param registeredAt   the current server time, in America/Lima time
     * @return the new log
     * @throws IllegalArgumentException when the moment is before the start or in the future
     */
    public static ActivityLog medicationOutcome(MedicationAdministration administration,
                                                RecordMedicationOutcomeCommand command,
                                                Long completedBy, LocalDateTime registeredAt) {
        Objects.requireNonNull(registeredAt, "Registration time cannot be null.");
        if (command.occurredAt().isBefore(administration.startsAt())) {
            throw new IllegalArgumentException("The occurredAt moment cannot be before the scheduled start "
                    + administration.startsAt() + ".");
        }
        if (command.occurredAt().isAfter(registeredAt)) {
            throw new IllegalArgumentException("The occurredAt moment cannot be in the future.");
        }
        var log = new ActivityLog();
        log.assistedPersonProfileId = administration.assistedPersonProfileId().personProfileId();
        log.careActivityId = administration.careActivityId();
        log.assignmentId = administration.id().assignmentId();
        log.date = administration.id().date();
        log.title = administration.medication();
        log.startTime = administration.startTime();
        log.endTime = administration.endTime();
        log.assignedCaregiverId = administration.caregiverId();
        log.status = command.result() == MedicationResult.ADMINISTERED
                ? ActivityLogStatus.COMPLETED : ActivityLogStatus.OMITTED;
        log.completedBy = completedBy;
        log.completedAt = command.occurredAt();
        log.notes = command.notes();
        log.registeredAt = registeredAt;
        log.late = command.occurredAt().isAfter(administration.endsAt());
        log.omissionReason = command.result() == MedicationResult.OMITTED ? command.reason() : null;
        return log;
    }

    /**
     * Returns the assisted person profile.
     *
     * @return the assisted person profile identifier
     */
    public PersonProfileId getAssistedPersonProfileId() {
        return new PersonProfileId(assistedPersonProfileId);
    }

    /**
     * Returns the time range in force that day.
     *
     * @return the time range
     */
    public TimeRange getTimeRange() {
        return new TimeRange(startTime, endTime);
    }
}
