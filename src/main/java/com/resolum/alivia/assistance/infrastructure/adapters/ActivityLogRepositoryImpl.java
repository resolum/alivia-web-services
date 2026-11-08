package com.resolum.alivia.assistance.infrastructure.adapters;

import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories.ActivityLogPersistenceRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class ActivityLogRepositoryImpl implements ActivityLogRepository {

    private final ActivityLogPersistenceRepository repository;

    public ActivityLogRepositoryImpl(ActivityLogPersistenceRepository repository) {
        this.repository = repository;
    }

    @Override
    public ActivityLog save(ActivityLog log) {
        return repository.save(log);
    }

    @Override
    public Optional<ActivityLog> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Optional<ActivityLog> findByAssignmentIdAndDate(Long assignmentId, LocalDate date) {
        return repository.findByAssignmentIdAndDate(assignmentId, date);
    }

    @Override
    public boolean existsByAssignmentIdAndDate(Long assignmentId, LocalDate date) {
        return repository.existsByAssignmentIdAndDate(assignmentId, date);
    }

    @Override
    public List<ActivityLog> findAllByAssistedPersonIdAndDateBetween(Long assistedPersonId, LocalDate from, LocalDate to) {
        return repository.findAllByAssistedPersonIdAndDateBetween(assistedPersonId, from, to);
    }

    @Override
    public List<ActivityLog> findAllByAssignedCaregiverIdAndDateBetween(Long caregiverId, LocalDate from, LocalDate to) {
        return repository.findAllByAssignedCaregiverIdAndDateBetween(caregiverId, from, to);
    }
}
