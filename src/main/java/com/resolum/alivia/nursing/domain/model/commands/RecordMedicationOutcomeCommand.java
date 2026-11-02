package com.resolum.alivia.nursing.domain.model.commands;

import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationId;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationResult;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Command to record the outcome of a medication administration.
 *
 * @param userId           the authenticated user
 * @param administrationId the administration
 * @param result           the informed result
 * @param occurredAt       the actual moment it happened, in America/Lima time
 * @param reason           the omission reason; required when the result is {@link MedicationResult#OMITTED}
 * @param notes            optional notes
 */
public record RecordMedicationOutcomeCommand(UserId userId, MedicationAdministrationId administrationId,
                                             MedicationResult result, LocalDateTime occurredAt,
                                             String reason, String notes) {

    /**
     * Longest accepted reason and notes, matching their columns.
     */
    public static final int MAX_TEXT_LENGTH = 500;

    /**
     * Validates the command. Rules that depend on the schedule or the current time live in
     * {@link com.resolum.alivia.nursing.domain.model.aggregates.ActivityLog}.
     *
     * @throws IllegalArgumentException when the result or the moment is missing, an omission has
     *                                  no reason, or a text is too long
     */
    public RecordMedicationOutcomeCommand {
        Objects.requireNonNull(userId, "User cannot be null.");
        Objects.requireNonNull(administrationId, "Administration cannot be null.");
        if (result == null) {
            throw new IllegalArgumentException("The result is required.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("The occurredAt moment is required.");
        }
        reason = reason == null || reason.isBlank() ? null : reason.strip();
        notes = notes == null || notes.isBlank() ? null : notes.strip();
        if (result == MedicationResult.OMITTED && reason == null) {
            throw new IllegalArgumentException("A reason is required when the medication is omitted.");
        }
        if ((reason != null && reason.length() > MAX_TEXT_LENGTH) || (notes != null && notes.length() > MAX_TEXT_LENGTH)) {
            throw new IllegalArgumentException("Reason and notes cannot exceed " + MAX_TEXT_LENGTH + " characters.");
        }
    }
}
