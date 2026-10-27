package com.resolum.alivia.hcm.interfaces.rest.resources;

import java.math.BigDecimal;

public record WorkScheduleSummaryResource(long totalSchedules, long employeesWithSchedule,
                                          BigDecimal averageWeeklyHours) {
}
