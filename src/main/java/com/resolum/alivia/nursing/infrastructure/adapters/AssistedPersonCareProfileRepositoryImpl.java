package com.resolum.alivia.nursing.infrastructure.adapters;

import com.resolum.alivia.nursing.domain.model.aggregates.AssistedPersonCareProfile;
import com.resolum.alivia.nursing.domain.repositories.AssistedPersonCareProfileRepository;
import com.resolum.alivia.nursing.infrastructure.persistence.jpa.repositories.AssistedPersonCareProfilePersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Implementation of {@link AssistedPersonCareProfileRepository} that uses PostgreSQL for data storage.
 */
@Repository
public class AssistedPersonCareProfileRepositoryImpl implements AssistedPersonCareProfileRepository {

    private final AssistedPersonCareProfilePersistenceRepository persistenceRepository;

    /**
     * Creates the adapter.
     *
     * @param persistenceRepository the Spring Data repository
     */
    public AssistedPersonCareProfileRepositoryImpl(AssistedPersonCareProfilePersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssistedPersonCareProfile save(AssistedPersonCareProfile profile) {
        return persistenceRepository.save(profile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<AssistedPersonCareProfile> findByRegisteredBy(UserId registeredBy) {
        return persistenceRepository.findByRegisteredByUserId(registeredBy.userId());
    }
}
