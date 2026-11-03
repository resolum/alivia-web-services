package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.LocalDate;

/** A block with capacity; it never says which technicians are free in it. */
public record TechnicalAvailabilityResource(String slotId, String serviceType, LocalDate date, String startTime,
                                            String endTime, int capacity) {
}
