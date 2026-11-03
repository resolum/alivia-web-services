package com.resolum.alivia.profiles.domain.repositories;

import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link Household} aggregates.
 */
public interface HouseholdRepository {

    /**
     * Saves a household with its members.
     *
     * @param household the household
     * @return the saved household
     */
    Household save(Household household);

    /**
     * Finds a household by identifier, with its members loaded.
     *
     * @param householdId the household identifier
     * @return the household, or empty when it does not exist
     */
    Optional<Household> findById(Long householdId);

    /**
     * Finds the households where the user has an active membership, with their members loaded.
     *
     * @param userId the member user
     * @return the matching households
     */
    List<Household> findActiveByMemberUserId(UserId userId);
}
