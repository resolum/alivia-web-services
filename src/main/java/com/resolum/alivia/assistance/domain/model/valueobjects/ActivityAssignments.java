package com.resolum.alivia.assistance.domain.model.valueobjects;

import com.resolum.alivia.assistance.domain.exceptions.ActivityAssignmentNotFoundException;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.OneToMany;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Component embedded in {@link CareActivity} that groups its assignments.
 */
@Embeddable
public class ActivityAssignments {

    @OneToMany(mappedBy = "careActivity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ActivityAssignment> activityAssignments;

    public ActivityAssignments() {
        this.activityAssignments = new ArrayList<>();
    }

    public List<ActivityAssignment> getActivityAssignments() {
        return List.copyOf(activityAssignments);
    }

    public ActivityAssignment getAssignmentWithId(Long assignmentId) {
        return getFirstAssignmentWhere(assignment -> Objects.equals(assignment.getId(), assignmentId));
    }

    public ActivityAssignment getFirstAssignmentWhere(Predicate<ActivityAssignment> predicate) {
        return activityAssignments.stream()
                .filter(predicate)
                .findFirst()
                .orElseThrow(() -> new ActivityAssignmentNotFoundException(null));
    }

    public ActivityAssignment addAssignment(CareActivity careActivity, Caregiver caregiver, Schedule schedule) {
        var assignment = new ActivityAssignment(careActivity, caregiver, schedule);
        activityAssignments.add(assignment);
        return assignment;
    }

    public void updateAssignment(Long assignmentId, Caregiver caregiver, Schedule schedule) {
        var assignment = getAssignmentWithId(assignmentId);
        assignment.update(caregiver, schedule);
    }

    public void activateAssignment(Long assignmentId) {
        var assignment = getAssignmentWithId(assignmentId);
        assignment.activate();
    }

    public void deactivateAssignment(Long assignmentId) {
        var assignment = getAssignmentWithId(assignmentId);
        assignment.deactivate();
    }

    public void reassignToRelative(Caregiver revokedCaregiver) {
        for (ActivityAssignment assignment : activityAssignments) {
            if (assignment.isAssignedTo(revokedCaregiver)) {
                assignment.update(null, assignment.getSchedule());
            }
        }
    }

    public List<ActivityAssignment> occurrencesOn(LocalDate date) {
        return activityAssignments.stream()
                .filter(assignment -> assignment.isActive() && assignment.occursOn(date))
                .toList();
    }

    public void clearAssignments() {
        this.activityAssignments.clear();
    }
}
