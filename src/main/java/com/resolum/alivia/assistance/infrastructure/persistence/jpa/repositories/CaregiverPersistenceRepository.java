package com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.valueobjects.CaregiverStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaregiverPersistenceRepository extends JpaRepository<Caregiver, Long> {
    List<Caregiver> findAllByAssistedPersonId(Long assistedPersonId);
    List<Caregiver> findAllByUserId(UserId userId);
    Optional<Caregiver> findByAssistedPersonIdAndUserIdAndStatus(Long assistedPersonId, UserId userId, CaregiverStatus status);
    boolean existsByAssistedPersonIdAndUserIdAndStatus(Long assistedPersonId, UserId userId, CaregiverStatus status);
}
