package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.Instant;
import java.time.LocalDate;

public record EvaluationSlotHoldResource(String draftId, LocalDate date, String startTime, String endTime,
                                         Long technicianId, String technicianName, Instant expiresAt) {
}
