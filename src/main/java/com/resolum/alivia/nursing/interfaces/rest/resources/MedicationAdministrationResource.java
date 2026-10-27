package com.resolum.alivia.nursing.interfaces.rest.resources;

import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * One scheduled medication administration and its recorded outcome.
 *
 * <p>This resource only describes what was scheduled and what was recorded. It never carries
 * clinical recommendations: no suggested doses, warnings or treatment changes.</p>
 *
 * @param administrationId the opaque administration identifier
 * @param medication       the medication, taken from the activity title
 * @param assistedPerson   the assisted person
 * @param date             the date the occurrence starts on
 * @param startTime        the scheduled start time
 * @param endTime          the scheduled end time
 * @param responsible      the responsible person
 * @param status           the administration status
 * @param outcome          the recorded outcome, or {@code null} while pending
 */
@Schema(name = "MedicationAdministration",
        description = "Scheduled medication administration; carries no clinical recommendations")
public record MedicationAdministrationResource(
        @Schema(description = "Opaque administration identifier", example = "12-20261026")
        String administrationId,
        @Schema(description = "Medication, taken from the activity title", example = "Losartan 50 mg")
        String medication,
        AssistedPersonResource assistedPerson,
        @Schema(description = "Date the occurrence starts on", example = "2026-10-26")
        LocalDate date,
        @Schema(description = "Scheduled start time", example = "08:00:00")
        LocalTime startTime,
        @Schema(description = "Scheduled end time", example = "09:00:00")
        LocalTime endTime,
        ResponsibleResource responsible,
        @Schema(description = "Administration status", example = "PENDING")
        MedicationAdministrationStatus status,
        @Schema(description = "Recorded outcome; null while pending", nullable = true)
        OutcomeResource outcome
) {

    /**
     * Assisted person summary.
     *
     * @param id   the assisted person profile identifier
     * @param name the full name, or {@code null} when it cannot be resolved
     */
    @Schema(name = "MedicationAdministrationAssistedPerson")
    public record AssistedPersonResource(
            @Schema(description = "Person profile identifier", example = "8") Long id,
            @Schema(description = "Full name; null when it cannot be resolved", nullable = true) String name) {
    }

    /**
     * Responsible person summary.
     *
     * @param type the role of the responsible person
     * @param id   the caregiver identifier, or {@code null} when the relative does it
     * @param name the full name, or {@code null} when it cannot be resolved
     */
    @Schema(name = "MedicationAdministrationResponsible")
    public record ResponsibleResource(
            @Schema(example = "CAREGIVER") CareRole type,
            @Schema(description = "Caregiver identifier; null when the relative does it",
                    example = "7", nullable = true) Long id,
            @Schema(description = "Full name; null when it cannot be resolved", nullable = true) String name) {
    }

    /**
     * Recorded outcome.
     *
     * @param result       the recorded result
     * @param occurredAt   the actual moment informed
     * @param registeredAt the server moment it was recorded
     * @param registeredBy the caregiver who recorded it, or {@code null} when a relative did
     * @param reason       the omission reason, or {@code null}
     * @param notes        the notes, or {@code null}
     * @param late         whether it happened after the scheduled end
     */
    @Schema(name = "MedicationAdministrationOutcome")
    public record OutcomeResource(
            @Schema(example = "ADMINISTERED") MedicationResult result,
            @Schema(description = "Actual moment, America/Lima time", example = "2026-10-26T08:15:00")
            LocalDateTime occurredAt,
            @Schema(description = "Server moment it was recorded, America/Lima time", example = "2026-10-26T08:20:00")
            LocalDateTime registeredAt,
            @Schema(description = "Caregiver identifier; null when a relative recorded it",
                    example = "7", nullable = true) Long registeredBy,
            @Schema(description = "Omission reason", nullable = true) String reason,
            @Schema(description = "Notes", nullable = true) String notes,
            @Schema(description = "Whether it happened after the scheduled end", example = "false") boolean late) {
    }
}
