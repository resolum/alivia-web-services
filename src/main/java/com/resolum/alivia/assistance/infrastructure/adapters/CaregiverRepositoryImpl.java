package com.resolum.alivia.assistance.infrastructure.adapters;

import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.valueobjects.CaregiverStatus;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories.CaregiverPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CaregiverRepositoryImpl implements CaregiverRepository {

    private final CaregiverPersistenceRepository repository;

    public CaregiverRepositoryImpl(CaregiverPersistenceRepository repository) {
        this.repository = repository;
    }

    @Override
    public Caregiver save(Caregiver caregiver) {
        return repository.save(caregiver);
    }

    @Override
    public Optional<Caregiver> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<Caregiver> findAllByAssistedPersonId(Long assistedPersonId) {
        return repository.findAllByAssistedPersonId(assistedPersonId);
    }

    @Override
    public List<Caregiver> findAllByUserId(UserId userId) {
        return repository.findAllByUserId(userId);
    }

    @Override
    public Optional<Caregiver> findByAssistedPersonIdAndUserIdAndStatus(Long assistedPersonId, UserId userId, CaregiverStatus status) {
        return repository.findByAssistedPersonIdAndUserIdAndStatus(assistedPersonId, userId, status);
    }

    @Override
    public boolean existsByAssistedPersonIdAndUserIdAndStatus(Long assistedPersonId, UserId userId, CaregiverStatus status) {
        return repository.existsByAssistedPersonIdAndUserIdAndStatus(assistedPersonId, userId, status);
    }
}
