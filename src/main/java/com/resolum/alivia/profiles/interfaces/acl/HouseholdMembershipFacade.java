package com.resolum.alivia.profiles.interfaces.acl;

import java.util.Optional;
import java.util.Set;

/**
 * Anti-corruption layer for other bounded contexts that need to read or change who belongs to a
 * household.
 *
 * <p>It exposes primitive types only. Authorization is not decided here: consumers must check
 * it first through {@link HouseholdAccessFacade}.</p>
 */
public interface HouseholdMembershipFacade {

    /**
     * Lists the users with an active membership in the household.
     *
     * @param householdId the household identifier
     * @return the user identifiers, or empty when the household does not exist
     */
    Optional<Set<Long>> fetchActiveMemberUserIds(Long householdId);

    /**
     * Adds a user to the household as an active member.
     *
     * @param householdId    the household identifier
     * @param userId         the user to add
     * @param responsibility the name of the household responsibility granted: {@code PRINCIPAL},
     *                       {@code CAREGIVER} or {@code GUEST}
     * @return {@code true} when the user was added; {@code false} when it was already a member,
     *         in which case its current membership is left untouched
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException
     *         when the household does not exist
     * @throws IllegalArgumentException when an identifier is not positive or the responsibility
     *                                  is unknown
     */
    boolean addMember(Long householdId, Long userId, String responsibility);
}
