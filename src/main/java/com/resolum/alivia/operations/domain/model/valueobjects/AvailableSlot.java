package com.resolum.alivia.operations.domain.model.valueobjects;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;

import java.time.LocalDate;

/**
 * A visit window the family can book with one of the technicians free in it.
 *
 * @param technicianId          technician that would attend it
 * @param availableTechnicians  how many technicians are free in the window
 */
public record AvailableSlot(LocalDate date, TimeRange window, Long technicianId, String technicianName,
                            int availableTechnicians) {
}
