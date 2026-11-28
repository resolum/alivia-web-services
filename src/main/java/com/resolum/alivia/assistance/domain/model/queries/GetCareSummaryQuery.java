package com.resolum.alivia.assistance.domain.model.queries;

import java.time.LocalDate;

/**
 * @param from the first day, included; required
 * @param to   the last day, included; {@code null} for today
 */
public record GetCareSummaryQuery(Long assistedPersonId, Long userId, LocalDate from, LocalDate to) {
}
