package com.resolum.alivia.assistance.domain.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;

import java.util.List;
import java.util.Optional;

public interface CareActivityRepository {
    CareActivity save(CareActivity careActivity);
    Optional<CareActivity> findById(Long id);
    List<CareActivity> findAllByAssistedPersonId(Long assistedPersonId);
    List<CareActivity> findAllByAssistedPersonRelativeId(Long relativeId);
    List<CareActivity> findAllActive();
}
