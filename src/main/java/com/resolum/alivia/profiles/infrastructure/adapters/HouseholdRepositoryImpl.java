package com.resolum.alivia.profiles.infrastructure.adapters;

import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdMemberStatus;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.HouseholdPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link HouseholdRepository} that uses PostgreSQL for data storage.
 */
@Repository
public class HouseholdRepositoryImpl implements HouseholdRepository {

    private final HouseholdPersistenceRepository persistenceRepository;

    /**
     * Creates the adapter.
     *
     * @param persistenceRepository the Spring Data repository
     */
    public HouseholdRepositoryImpl(HouseholdPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Household save(Household household) {
        return persistenceRepository.save(household);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Household> findById(Long householdId) {
        return persistenceRepository.findWithMembersById(householdId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Household> findActiveByMemberUserId(UserId userId) {
        return persistenceRepository.findAllByMemberUserIdAndStatus(
                userId.userId(), HouseholdMemberStatus.ACTIVE);
    }
}
