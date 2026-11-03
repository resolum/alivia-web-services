package com.resolum.alivia.business.infrastructure.adapters;

import com.resolum.alivia.business.domain.model.aggregates.Business;
import com.resolum.alivia.business.domain.repositories.BusinessRepository;
import com.resolum.alivia.business.infrastructure.persistence.jpa.repositories.BusinessPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class BusinessRepositoryImpl implements BusinessRepository {

    private final BusinessPersistenceRepository persistenceRepository;

    public BusinessRepositoryImpl(BusinessPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<Business> findById(Long id) {
        return persistenceRepository.findById(id);
    }

    @Override
    public Optional<Business> findByAdministratorId(Long administratorId) {
        return persistenceRepository.findByAdministratorId(administratorId);
    }

    @Override
    public boolean existsByAdministratorId(Long administratorId) {
        return persistenceRepository.existsByAdministratorId(administratorId);
    }

    @Override
    public Business save(Business business) {
        return persistenceRepository.save(business);
    }
}
