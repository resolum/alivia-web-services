package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDate;

/**
 * @param status       COMPLETED, or CANCELLED (OMITTED is accepted as a synonym)
 * @param assignmentId the occurrence's assignment; optional when the activity has a single occurrence that day
 * @param date         the day of the occurrence; today when absent
 */
public record UpdateCareActivityStatusResource(String status, Long assignmentId, LocalDate date, String notes) {
}
