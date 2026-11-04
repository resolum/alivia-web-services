package com.resolum.alivia.hcm.interfaces.acl;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    /**
     * Employee identifier of the active technician signed in with {@code userId}, the identifier Operations
     * stores as the technician of an order.
     *
     * @param userId the IAM user identifier
     * @return the employee identifier, or empty when the account is not an active technician
     */
    Optional<Long> fetchTechnicianIdOfUser(Long userId);
}
