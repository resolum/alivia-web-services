package com.resolum.alivia.tracking.domain.services;

/**
 * Decides which homes a user may read in Tracking, from who takes care of the people who live there. A user is not
 * assumed to be a member of the household: the relative in charge and the caregivers of an assisted person read it
 * through the care network of the person.
 */
public interface TrackingAccessService {

    /**
     * The household of an assisted person the user may read.
     *
     * @throws com.resolum.alivia.tracking.domain.exceptions.AssistedPersonNotAccessible when the person does not exist
     *         or the user may not read it (the same answer for both)
     * @throws com.resolum.alivia.tracking.domain.exceptions.AssistedPersonWithoutHousehold when the user may read the
     *         person but the person has no household
     */
    Long requireReadableHouseholdOfPerson(Long userId, Long assistedPersonId);

    /**
     * The household where the device is placed, when the user may read it.
     *
     * @throws com.resolum.alivia.tracking.domain.exceptions.DeviceNotAccessible when the device does not exist, is not
     *         placed in a home, or the user may not read that home (the same answer for all)
     */
    Long requireReadableHouseholdOfDevice(Long userId, Long deviceId);
}
