package com.resolum.alivia.nursing.infrastructure.adapters;

import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.valueobjects.MembershipStatus;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.nursing.infrastructure.persistence.jpa.repositories.CareMembershipPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Implementation of {@link CareMembershipRepository} that uses PostgreSQL for data storage.
 */
@Repository
public class CareMembershipRepositoryImpl implements CareMembershipRepository {

    private final CareMembershipPersistenceRepository persistenceRepository;

    /**
     * Creates the adapter.
     *
     * @param persistenceRepository the Spring Data repository
     */
    public CareMembershipRepositoryImpl(CareMembershipPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CareMembership> findActiveByUserId(UserId userId) {
        return persistenceRepository.findAllByUserIdAndStatus(userId.userId(), MembershipStatus.ACTIVE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CareMembership> findActiveByAssistedPersonProfileIds(
            Collection<PersonProfileId> assistedPersonProfileIds) {
        if (assistedPersonProfileIds.isEmpty()) {
            return List.of();
        }
        return persistenceRepository.findAllByAssistedPersonProfileIdInAndStatus(
                assistedPersonProfileIds.stream().map(PersonProfileId::personProfileId).toList(),
                MembershipStatus.ACTIVE);
    }
}
