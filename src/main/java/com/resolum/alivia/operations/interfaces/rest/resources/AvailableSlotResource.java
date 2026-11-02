package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.LocalDate;

public record AvailableSlotResource(LocalDate date, String startTime, String endTime, Long technicianId,
                                    String technicianName, int availableTechnicians) {
}
