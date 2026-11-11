package com.resolum.alivia.assistance.domain.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.valueobjects.CaregiverStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

public interface CaregiverRepository {
    Caregiver save(Caregiver caregiver);
    Optional<Caregiver> findById(Long id);
    List<Caregiver> findAllByAssistedPersonId(Long assistedPersonId);
    List<Caregiver> findAllByUserId(UserId userId);
    Optional<Caregiver> findByAssistedPersonIdAndUserIdAndStatus(Long assistedPersonId, UserId userId, CaregiverStatus status);
    boolean existsByAssistedPersonIdAndUserIdAndStatus(Long assistedPersonId, UserId userId, CaregiverStatus status);
}
