package com.resolum.alivia.operations.domain.model.queries;

import java.time.LocalDate;

/** The shifts the signed-in technician works between two dates, both included (at most 62 days). */
public record GetTechnicianScheduleQuery(Long userId, LocalDate from, LocalDate to) {
}
