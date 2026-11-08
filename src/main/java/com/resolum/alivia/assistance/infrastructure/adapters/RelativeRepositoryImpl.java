package com.resolum.alivia.assistance.infrastructure.adapters;

import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories.RelativePersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RelativeRepositoryImpl implements RelativeRepository {

    private final RelativePersistenceRepository repository;

    public RelativeRepositoryImpl(RelativePersistenceRepository repository) {
        this.repository = repository;
    }

    @Override
    public Relative save(Relative relative) {
        return repository.save(relative);
    }

    @Override
    public Optional<Relative> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Relative> findByUserId(UserId userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public List<Relative> findAllByBusinessId(BusinessId businessId) {
        return repository.findAllByBusinessId(businessId);
    }

    @Override
    public boolean existsByUserId(UserId userId) {
        return repository.existsByUserId(userId);
    }
}
