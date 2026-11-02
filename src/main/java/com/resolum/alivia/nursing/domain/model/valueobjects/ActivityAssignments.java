package com.resolum.alivia.nursing.domain.model.valueobjects;

import com.resolum.alivia.nursing.domain.exceptions.ActivityAssignmentNotFoundException;
import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.entities.ActivityAssignment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.OneToMany;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Component embedded in {@link CareActivity} that groups its assignments.
 *
 * <p>It is not a strict value object: it is mutable and holds entities with identity.</p>
 */
@Embeddable
public class ActivityAssignments {

    @OneToMany(mappedBy = "careActivity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ActivityAssignment> activityAssignments;

    /**
     * Creates an empty component.
     */
    public ActivityAssignments() {
        this.activityAssignments = new ArrayList<>();
    }

    /**
     * Returns every assignment.
     *
     * @return an unmodifiable copy of the assignments
     */
    public List<ActivityAssignment> getActivityAssignments() {
        return List.copyOf(activityAssignments);
    }

    /**
     * Returns the assignment with the given identifier.
     *
     * @param assignmentId the assignment identifier
     * @return the assignment, never {@code null}
     * @throws ActivityAssignmentNotFoundException when it does not belong to this activity
     */
    public ActivityAssignment getAssignmentWithId(Long assignmentId) {
        return activityAssignments.stream()
                .filter(assignment -> Objects.equals(assignment.getId(), assignmentId))
                .findFirst()
                .orElseThrow(() -> new ActivityAssignmentNotFoundException(assignmentId));
    }

    /**
     * Adds an active assignment.
     *
     * @param careActivity the owning activity
     * @param caregiverId  the caregiver, or {@code null} when the relative does it
     * @param schedule     the schedule
     * @return the new assignment
     */
    public ActivityAssignment addAssignment(CareActivity careActivity, Long caregiverId, Schedule schedule) {
        var assignment = new ActivityAssignment(careActivity, caregiverId, schedule);
        activityAssignments.add(assignment);
        return assignment;
    }

    /**
     * Returns the active assignments with an occurrence starting on the given date.
     *
     * @param date the date
     * @return the matching assignments
     */
    public List<ActivityAssignment> occurrencesOn(LocalDate date) {
        return activityAssignments.stream()
                .filter(assignment -> assignment.isActive() && assignment.occursOn(date))
                .toList();
    }
}
