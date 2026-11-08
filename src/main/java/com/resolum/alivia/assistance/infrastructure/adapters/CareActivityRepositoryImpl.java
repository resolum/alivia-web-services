package com.resolum.alivia.assistance.infrastructure.adapters;

import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.repositories.CareActivityRepository;
import com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories.CareActivityPersistenceRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CareActivityRepositoryImpl implements CareActivityRepository {

    private final CareActivityPersistenceRepository repository;

    public CareActivityRepositoryImpl(CareActivityPersistenceRepository repository) {
        this.repository = repository;
    }

    @Override
    public CareActivity save(CareActivity careActivity) {
        return repository.save(careActivity);
    }

    @Override
    public Optional<CareActivity> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<CareActivity> findAllByAssistedPersonId(Long assistedPersonId) {
        return repository.findAllByAssistedPersonId(assistedPersonId);
    }

    @Override
    public List<CareActivity> findAllByAssistedPersonRelativeId(Long relativeId) {
        return repository.findAllByAssistedPersonRelativeId(relativeId);
    }

    @Override
    public List<CareActivity> findAllActive() {
        return repository.findAllActive();
    }
}
