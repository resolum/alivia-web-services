package com.resolum.alivia.profiles.domain.services;

/**
 * Decides whether a user may read or manage a household.
 *
 * <p>The decision depends only on the membership stored in the household aggregate: an active
 * membership with the required responsibility grants the operation. The global access role of
 * the token is never considered.</p>
 *
 * <p>Responsibility matrix: {@code PRINCIPAL} reads and manages; {@code CAREGIVER} and
 * {@code GUEST} only read; a revoked membership or no membership grants nothing.</p>
 */
public interface HouseholdAccessService {

    /**
     * Checks whether the user may read the household and its members.
     *
     * @param householdId the household identifier
     * @param userId      the authenticated user
     * @return {@code true} when the user has an active membership with read access;
     *         {@code false} otherwise, including when the household does not exist
     * @throws IllegalArgumentException when either identifier is missing or not positive
     */
    boolean canRead(Long householdId, Long userId);

    /**
     * Checks whether the user may manage the household.
     *
     * @param householdId the household identifier
     * @param userId      the authenticated user
     * @return {@code true} when the user has an active membership with management access;
     *         {@code false} otherwise, including when the household does not exist
     * @throws IllegalArgumentException when either identifier is missing or not positive
     */
    boolean canManage(Long householdId, Long userId);

    /**
     * Requires read access to the household.
     *
     * @param householdId the household identifier
     * @param userId      the authenticated user
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException
     *         when the household does not exist
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdAccessDeniedException
     *         when the user has no active membership with read access
     * @throws IllegalArgumentException when either identifier is missing or not positive
     */
    void assertCanRead(Long householdId, Long userId);

    /**
     * Requires management access to the household.
     *
     * @param householdId the household identifier
     * @param userId      the authenticated user
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException
     *         when the household does not exist
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdAccessDeniedException
     *         when the user has no active membership with management access
     * @throws IllegalArgumentException when either identifier is missing or not positive
     */
    void assertCanManage(Long householdId, Long userId);
}
