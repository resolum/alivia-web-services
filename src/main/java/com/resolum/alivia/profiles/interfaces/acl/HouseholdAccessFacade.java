package com.resolum.alivia.profiles.interfaces.acl;

/**
 * Anti-corruption layer for other bounded contexts that need to authorize an operation on a
 * household.
 *
 * <p>It exposes booleans only: the consumer decides which error to raise in its own context.
 * A missing household and a denied membership both return {@code false}, so consumers never
 * depend on Profiles exceptions.</p>
 */
public interface HouseholdAccessFacade {

    /**
     * Checks whether the user may read the household and its members.
     *
     * @param householdId the household identifier
     * @param userId      the authenticated user
     * @return {@code true} when the user has an active membership with read access;
     *         {@code false} otherwise, including when the household does not exist
     */
    boolean canReadHousehold(Long householdId, Long userId);

    /**
     * Checks whether the user may manage the household.
     *
     * @param householdId the household identifier
     * @param userId      the authenticated user
     * @return {@code true} when the user has an active membership with management access;
     *         {@code false} otherwise, including when the household does not exist
     */
    boolean canManageHousehold(Long householdId, Long userId);
}
