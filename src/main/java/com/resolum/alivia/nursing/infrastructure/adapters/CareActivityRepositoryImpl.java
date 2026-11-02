package com.resolum.alivia.nursing.infrastructure.adapters;

import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.repositories.CareActivityRepository;
import com.resolum.alivia.nursing.infrastructure.persistence.jpa.repositories.CareActivityPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link CareActivityRepository} that uses PostgreSQL for data storage.
 */
@Repository
public class CareActivityRepositoryImpl implements CareActivityRepository {

    private final CareActivityPersistenceRepository persistenceRepository;

    /**
     * Creates the adapter.
     *
     * @param persistenceRepository the Spring Data repository
     */
    public CareActivityRepositoryImpl(CareActivityPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CareActivity> findAllByAssistedPersonProfileIds(Collection<PersonProfileId> assistedPersonProfileIds) {
        if (assistedPersonProfileIds.isEmpty()) {
            return List.of();
        }
        return persistenceRepository.findAllByAssistedPersonProfileIdIn(
                assistedPersonProfileIds.stream().map(PersonProfileId::personProfileId).toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CareActivity> findByAssignmentId(Long assignmentId) {
        return persistenceRepository.findByAssignmentId(assignmentId);
    }
}
