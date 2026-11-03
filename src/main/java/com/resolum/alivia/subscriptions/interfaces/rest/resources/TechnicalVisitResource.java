package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import java.time.LocalDate;

/**
 * @param status {@code SCHEDULED} or {@code COMPLETED}
 * @param result the technician's decision once completed
 */
public record TechnicalVisitResource(LocalDate date, String startTime, String endTime, String technicianName,
                                     String directions, String status, String result, String notes) {
}
