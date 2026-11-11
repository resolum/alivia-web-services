package com.resolum.alivia.assistance.domain.model.aggregates;

import com.resolum.alivia.assistance.domain.exceptions.CareActivityNotActiveException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverNotActiveException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.InvalidScheduleException;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.assistance.domain.model.valueobjects.ActivityAssignments;
import com.resolum.alivia.assistance.domain.model.valueobjects.ActivityStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.model.valueobjects.Schedule;
import com.resolum.alivia.assistance.domain.model.valueobjects.ScheduleType;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Care activity of an assisted person, together with its assignments.
 */
@Entity
@Table(name = "assistance_care_activities")
@Getter
public class CareActivity extends AuditableAbstractAggregateRoot<CareActivity> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assisted_person_id", nullable = false)
    private AssistedPerson assistedPerson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CareActivityType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityStatus status;

    @Embedded
    private ActivityAssignments activityAssignments;

    protected CareActivity() {
        this.activityAssignments = new ActivityAssignments();
    }

    public CareActivity(AssistedPerson assistedPerson, CareActivityType type, String title, String description) {
        this();
        this.assistedPerson = Objects.requireNonNull(assistedPerson, "Assisted person cannot be null.");
        this.type = Objects.requireNonNull(type, "Activity type cannot be null.");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Activity title cannot be blank.");
        }
        this.title = title.strip();
        this.description = description == null || description.isBlank() ? null : description.strip();
        this.status = ActivityStatus.ACTIVE;
    }

    public void update(CareActivityType type, String title, String description) {
        if (type != null) {
            this.type = type;
        }
        if (title != null && !title.isBlank()) {
            this.title = title.strip();
        }
        this.description = description == null || description.isBlank() ? null : description.strip();
    }

    public void activate() {
        this.status = ActivityStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ActivityStatus.INACTIVE;
    }

    public ActivityAssignment assign(Caregiver caregiver, Schedule schedule, LocalDate today) {
        validateAssignmentParameters(caregiver, schedule, today);
        return activityAssignments.addAssignment(this, caregiver, schedule);
    }

    public void updateAssignment(Long assignmentId, Caregiver caregiver, Schedule schedule, LocalDate today) {
        validateAssignmentParameters(caregiver, schedule, today);
        activityAssignments.updateAssignment(assignmentId, caregiver, schedule);
    }

    public void activateAssignment(Long assignmentId) {
        activityAssignments.activateAssignment(assignmentId);
    }

    public void deactivateAssignment(Long assignmentId) {
        activityAssignments.deactivateAssignment(assignmentId);
    }

    public void reassignToRelative(Caregiver revokedCaregiver) {
        activityAssignments.reassignToRelative(revokedCaregiver);
    }

    public List<ActivityAssignment> occurrencesOn(LocalDate date) {
        return activityAssignments.occurrencesOn(date);
    }

    public boolean isActive() {
        return status == ActivityStatus.ACTIVE;
    }

    public PersonProfileId getAssistedPersonProfileId() {
        return assistedPerson != null ? assistedPerson.getPersonProfileId() : null;
    }

    private void validateAssignmentParameters(Caregiver caregiver, Schedule schedule, LocalDate today) {
        if (!isActive()) {
            throw new CareActivityNotActiveException(getId());
        }
        if (caregiver != null) {
            if (!caregiver.isActive()) {
                throw new CaregiverNotActiveException(caregiver.getId());
            }
            if (!caregiver.isCaregiverOf(assistedPerson)) {
                throw new CaregiverNotFoundException(caregiver.getId());
            }
        }
        Objects.requireNonNull(schedule, "Schedule cannot be null.");
        if (schedule.type() == ScheduleType.ONE_TIME && schedule.date().isBefore(today)) {
            throw new InvalidScheduleException("A one-time schedule cannot be before " + today + ".");
        }
    }
}
