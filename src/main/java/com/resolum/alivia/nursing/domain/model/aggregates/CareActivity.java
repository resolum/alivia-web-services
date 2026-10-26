package com.resolum.alivia.nursing.domain.model.aggregates;

import com.resolum.alivia.nursing.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.nursing.domain.model.valueobjects.ActivityAssignments;
import com.resolum.alivia.nursing.domain.model.valueobjects.ActivityStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.nursing.domain.model.valueobjects.Schedule;
import com.resolum.alivia.nursing.domain.model.valueobjects.ScheduleType;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Care activity of an assisted person, together with its assignments (shifts).
 *
 * <p>Only the assisted person profile identifier is stored; names live in Profiles. A new
 * activity starts {@link ActivityStatus#ACTIVE}.</p>
 */
@Entity
@Getter
public class CareActivity extends AuditableAbstractAggregateRoot<CareActivity> {

    @Column(nullable = false)
    private Long assistedPersonProfileId;

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

    /**
     * Creates an active activity without assignments.
     *
     * @param assistedPersonProfileId the assisted person profile
     * @param type                    the activity type
     * @param title                   the activity title
     * @param description             optional description
     */
    public CareActivity(PersonProfileId assistedPersonProfileId, CareActivityType type, String title,
                        String description) {
        this();
        this.assistedPersonProfileId = Objects.requireNonNull(assistedPersonProfileId,
                "Assisted person cannot be null.").personProfileId();
        this.type = Objects.requireNonNull(type, "Activity type cannot be null.");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Activity title cannot be blank.");
        }
        this.title = title.strip();
        this.description = description == null || description.isBlank() ? null : description.strip();
        this.status = ActivityStatus.ACTIVE;
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
     * Assigns the activity to a caregiver, or to the relative, on a schedule.
     *
     * @param caregiverId the caregiver membership, or {@code null} when the relative does it
     * @param schedule    the schedule
     * @param today       the current date in America/Lima
     * @return the new assignment
     * @throws IllegalStateException    when the activity is not active
     * @throws IllegalArgumentException when a one-time schedule is before today
     */
    public ActivityAssignment assign(Long caregiverId, Schedule schedule, LocalDate today) {
        if (!isActive()) {
            throw new IllegalStateException("Care activity " + getId() + " is not active.");
        }
        Objects.requireNonNull(schedule, "Schedule cannot be null.");
        if (schedule.type() == ScheduleType.ONE_TIME && schedule.date().isBefore(today)) {
            throw new IllegalArgumentException("A one-time schedule cannot be before " + today + ".");
        }
        return activityAssignments.addAssignment(this, caregiverId, schedule);
    }

    /**
     * Returns the active assignments with an occurrence starting on the given date.
     *
     * @param date the date
     * @return the matching assignments
     */
    public List<ActivityAssignment> occurrencesOn(LocalDate date) {
        return activityAssignments.occurrencesOn(date);
    }

    /**
     * Indicates whether the activity is active.
     *
     * @return {@code true} when active
     */
    public boolean isActive() {
        return status == ActivityStatus.ACTIVE;
    }
}
