package com.resolum.alivia.operations.domain.model.valueobjects;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;

import java.time.LocalDate;

/**
 * A visit window the family can book.
 *
 * @param technicianId          technician that would attend it (the least busy one that day)
 * @param availableTechnicians  how many technicians could take it
 */
public record AvailableSlot(LocalDate date, TimeRange window, Long technicianId, String technicianName,
                            int availableTechnicians) {
}
