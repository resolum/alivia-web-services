package com.resolum.alivia.nursing.infrastructure.adapters;

import com.resolum.alivia.nursing.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.nursing.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.nursing.infrastructure.persistence.jpa.repositories.ActivityLogPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link ActivityLogRepository} that uses PostgreSQL for data storage.
 */
@Repository
public class ActivityLogRepositoryImpl implements ActivityLogRepository {

    private final ActivityLogPersistenceRepository persistenceRepository;

    /**
     * Creates the adapter.
     *
     * @param persistenceRepository the Spring Data repository
     */
    public ActivityLogRepositoryImpl(ActivityLogPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ActivityLog> findAllByAssignmentIdsBetween(Collection<Long> assignmentIds,
                                                           LocalDate from, LocalDate to) {
        if (assignmentIds.isEmpty()) {
            return List.of();
        }
        return persistenceRepository.findAllByAssignmentIdInAndDateBetween(assignmentIds, from, to);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ActivityLog> findByAssignmentIdAndDate(Long assignmentId, LocalDate date) {
        return persistenceRepository.findByAssignmentIdAndDate(assignmentId, date);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivityLog save(ActivityLog log) {
        return persistenceRepository.saveAndFlush(log);
    }
}
