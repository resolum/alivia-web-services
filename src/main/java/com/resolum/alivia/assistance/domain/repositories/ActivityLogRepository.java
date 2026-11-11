package com.resolum.alivia.assistance.domain.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ActivityLogRepository {
    ActivityLog save(ActivityLog log);
    Optional<ActivityLog> findById(Long id);
    Optional<ActivityLog> findByAssignmentIdAndDate(Long assignmentId, LocalDate date);
    boolean existsByAssignmentIdAndDate(Long assignmentId, LocalDate date);
    List<ActivityLog> findAllByAssistedPersonIdAndDateBetween(Long assistedPersonId, LocalDate from, LocalDate to);
    List<ActivityLog> findAllByAssignedCaregiverIdAndDateBetween(Long caregiverId, LocalDate from, LocalDate to);
}
