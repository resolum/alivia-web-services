package com.resolum.alivia.nursing.interfaces.rest.resources;

import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Body to record the outcome of a medication administration.
 *
 * @param result     the result
 * @param occurredAt the actual moment, in America/Lima time
 * @param reason     the omission reason; required when the result is {@code OMITTED}
 * @param notes      optional notes
 */
@Schema(name = "RecordMedicationOutcome")
public record RecordMedicationOutcomeResource(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ADMINISTERED")
        MedicationResult result,
        @Schema(description = "Actual moment, America/Lima time; not in the future and not before the scheduled start",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-26T08:15:00")
        LocalDateTime occurredAt,
        @Schema(description = "Required when the result is OMITTED", example = "Refused to take it", nullable = true)
        String reason,
        @Schema(description = "Optional notes", nullable = true)
        String notes
) {
}
