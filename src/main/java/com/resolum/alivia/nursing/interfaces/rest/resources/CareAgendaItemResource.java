package com.resolum.alivia.nursing.interfaces.rest.resources;

import com.resolum.alivia.nursing.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.ReminderPriority;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * One occurrence of the care agenda, also used for reminders.
 *
 * @param activity       the activity
 * @param assignmentId   the assignment (shift) identifier
 * @param assistedPerson the assisted person
 * @param date           the date the occurrence starts on
 * @param startTime      the scheduled start time
 * @param endTime        the scheduled end time
 * @param startsAt       the actual start
 * @param endsAt         the actual end
 * @param responsible    the responsible person
 * @param recurring      whether the assignment repeats weekly
 * @param status         the computed status
 * @param priority       the priority derived from the activity type
 */
@Schema(name = "CareAgendaItem", description = "One occurrence of an activity assignment in the care agenda")
public record CareAgendaItemResource(
        ActivityResource activity,
        @Schema(description = "Assignment (shift) identifier", example = "12")
        Long assignmentId,
        AssistedPersonResource assistedPerson,
        @Schema(description = "Date the occurrence starts on", example = "2026-10-26")
        LocalDate date,
        @Schema(description = "Scheduled start time", example = "22:00:00")
        LocalTime startTime,
        @Schema(description = "Scheduled end time", example = "06:00:00")
        LocalTime endTime,
        @Schema(description = "Actual start", example = "2026-10-26T22:00:00")
        LocalDateTime startsAt,
        @Schema(description = "Actual end; next day when the range crosses midnight", example = "2026-10-27T06:00:00")
        LocalDateTime endsAt,
        ResponsibleResource responsible,
        @Schema(description = "Whether the assignment repeats weekly", example = "true")
        boolean recurring,
        @Schema(description = "Computed occurrence status", example = "SCHEDULED")
        OccurrenceStatus status,
        @Schema(description = "Priority derived from the activity type", example = "HIGH")
        ReminderPriority priority
) {

    /**
     * Activity summary.
     *
     * @param id    the activity identifier
     * @param title the activity title
     * @param type  the activity type
     */
    @Schema(name = "CareAgendaActivity")
    public record ActivityResource(
            @Schema(example = "3") Long id,
            @Schema(example = "Morning medication") String title,
            @Schema(example = "MEDICATION") CareActivityType type) {
    }

    /**
     * Assisted person summary.
     *
     * @param id   the assisted person profile identifier
     * @param name the full name, or {@code null} when it cannot be resolved
     */
    @Schema(name = "CareAgendaAssistedPerson")
    public record AssistedPersonResource(
            @Schema(description = "Person profile identifier", example = "8") Long id,
            @Schema(description = "Full name; null when it cannot be resolved", nullable = true) String name) {
    }

    /**
     * Responsible person summary.
     *
     * @param type             the role of the responsible person
     * @param id               the caregiver identifier, or {@code null} when the relative does it
     * @param personProfileId  the caregiver person profile, or {@code null} without an active membership
     * @param name             the full name, or {@code null} when it cannot be resolved
     * @param email            the email from IAM, or {@code null} when it cannot be resolved
     */
    @Schema(name = "CareAgendaResponsible")
    public record ResponsibleResource(
            @Schema(example = "CAREGIVER") CareRole type,
            @Schema(description = "Caregiver identifier; null when the relative does it",
                    example = "7", nullable = true) Long id,
            @Schema(description = "Person profile identifier; null without an active membership",
                    example = "15", nullable = true) Long personProfileId,
            @Schema(description = "Full name; null when it cannot be resolved", nullable = true) String name,
            @Schema(description = "Email address; null when it cannot be resolved",
                    example = "ana.torres@example.com", nullable = true) String email) {
    }
}
