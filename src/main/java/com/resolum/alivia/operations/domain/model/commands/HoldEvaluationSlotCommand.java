package com.resolum.alivia.operations.domain.model.commands;

import java.time.LocalDate;
import java.time.LocalTime;

/** The family picks the evaluation window and technician before paying, so nobody else takes it meanwhile. */
public record HoldEvaluationSlotCommand(String draftId, LocalDate date, LocalTime startTime, LocalTime endTime,
                                        Long technicianId) {
}
