package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDateTime;

public record RecordMedicationOutcomeResource(
        String result,
        String reason,
        LocalDateTime occurredAt
) {}
