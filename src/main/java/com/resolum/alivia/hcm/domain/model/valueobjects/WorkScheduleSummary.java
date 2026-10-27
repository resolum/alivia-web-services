package com.resolum.alivia.hcm.domain.model.valueobjects;

import java.math.BigDecimal;

/** Totals shown on top of the work schedules list. */
public record WorkScheduleSummary(long totalSchedules, long employeesWithSchedule, BigDecimal averageWeeklyHours) {
}
