package com.resolum.alivia.assistance.interfaces.rest.resources;

import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.ReminderPriority;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * One occurrence of the care agenda, matching frontend AgendaOccurrenceResource.
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

    @Schema(name = "CareAgendaActivity")
    public record ActivityResource(
            @Schema(example = "3") Long id,
            @Schema(example = "Morning medication") String title,
            @Schema(example = "MEDICATION") CareActivityType type) {
    }

    @Schema(name = "CareAgendaAssistedPerson")
    public record AssistedPersonResource(
            @Schema(description = "Person profile identifier", example = "8") Long id,
            @Schema(description = "Full name; null when it cannot be resolved", nullable = true) String name) {
    }

    @Schema(name = "CareAgendaResponsible")
    public record ResponsibleResource(
            @Schema(example = "CAREGIVER") String type,
            @Schema(description = "Caregiver identifier; null when the relative does it",
                    example = "7", nullable = true) Long id,
            @Schema(description = "Person profile identifier; null without an active membership",
                    example = "15", nullable = true) Long personProfileId,
            @Schema(description = "Full name; null when it cannot be resolved", nullable = true) String name,
            @Schema(description = "Email address; null when it cannot be resolved",
                    example = "ana.torres@example.com", nullable = true) String email) {
    }
}
