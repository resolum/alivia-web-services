package com.resolum.alivia.iam.infrastructure.adapters;

import com.resolum.alivia.iam.domain.exceptions.EdgeCredentialConflictException;
import com.resolum.alivia.iam.domain.model.aggregates.EdgeCredential;
import com.resolum.alivia.iam.domain.repositories.EdgeCredentialRepository;
import com.resolum.alivia.iam.infrastructure.persistence.jpa.repositories.EdgeCredentialPersistenceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementation of {@link EdgeCredentialRepository} that uses PostgreSQL for data storage.
 */
@Repository
public class EdgeCredentialRepositoryImpl implements EdgeCredentialRepository {

    private final EdgeCredentialPersistenceRepository persistenceRepository;

    public EdgeCredentialRepositoryImpl(EdgeCredentialPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<EdgeCredential> findByEdgeId(Long edgeId) {
        return persistenceRepository.findByEdgeId(edgeId);
    }

    @Override
    public Optional<EdgeCredential> findByEdgeIdForUpdate(Long edgeId) {
        return persistenceRepository.findByEdgeIdForUpdate(edgeId);
    }

    /**
     * {@inheritDoc}
     *
     * @throws EdgeCredentialConflictException when the unique constraint on the edge is violated
     */
    @Override
    public EdgeCredential save(EdgeCredential credential) {
        try {
            return persistenceRepository.saveAndFlush(credential);
        } catch (DataIntegrityViolationException exception) {
            throw new EdgeCredentialConflictException();
        }
    }

    @Override
    public boolean recordUse(Long edgeId, LocalDateTime usedAt, LocalDateTime staleBefore) {
        return persistenceRepository.recordUse(edgeId, usedAt, staleBefore) > 0;
    }
}
