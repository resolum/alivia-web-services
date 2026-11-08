package com.resolum.alivia.assistance.infrastructure.adapters;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories.AssistedPersonPersistenceRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AssistedPersonRepositoryImpl implements AssistedPersonRepository {

    private final AssistedPersonPersistenceRepository repository;

    public AssistedPersonRepositoryImpl(AssistedPersonPersistenceRepository repository) {
        this.repository = repository;
    }

    @Override
    public AssistedPerson save(AssistedPerson assistedPerson) {
        return repository.save(assistedPerson);
    }

    @Override
    public Optional<AssistedPerson> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<AssistedPerson> findAllByRelativeId(Long relativeId) {
        return repository.findAllByRelativeId(relativeId);
    }

    @Override
    public List<AssistedPerson> findAll() {
        return repository.findAll();
    }
}
