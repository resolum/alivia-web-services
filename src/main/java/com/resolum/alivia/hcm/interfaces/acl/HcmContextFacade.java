package com.resolum.alivia.hcm.interfaces.acl;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;

import java.time.LocalDate;
import java.util.List;

/**
 * Anti-corruption layer of HCM for the contexts that send technicians to a home.
 *
 * <p>A technician works on a date when they are an active employee with the {@code TECH} role, have a
 * contract in force that day and an active work schedule in effect that day.</p>
 */
public interface HcmContextFacade {

    /**
     * A technician and the shifts they work on a given date.
     *
     * @param employeeId employee identifier
     * @param fullName   name shown to the family
     * @param shifts     the shift blocks of that weekday, in order
     */
    record TechnicianShifts(Long employeeId, String fullName, List<TimeRange> shifts) {
    }

    /** Technicians that work on {@code date}, with that day's shifts. Empty when nobody works. */
    List<TechnicianShifts> fetchTechniciansWorkingOn(LocalDate date);
}
