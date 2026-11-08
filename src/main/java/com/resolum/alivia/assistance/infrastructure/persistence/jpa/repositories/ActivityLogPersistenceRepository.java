package com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityLogPersistenceRepository extends JpaRepository<ActivityLog, Long> {
    Optional<ActivityLog> findByAssignmentIdAndDate(Long assignmentId, LocalDate date);
    boolean existsByAssignmentIdAndDate(Long assignmentId, LocalDate date);
    List<ActivityLog> findAllByAssistedPersonIdAndDateBetween(Long assistedPersonId, LocalDate from, LocalDate to);
    List<ActivityLog> findAllByAssignedCaregiverIdAndDateBetween(Long caregiverId, LocalDate from, LocalDate to);
}
