package com.resolum.alivia.assistance.domain.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.Handover;

import java.util.List;
import java.util.Optional;

public interface HandoverRepository {
    Handover save(Handover handover);
    Optional<Handover> findById(Long id);
    List<Handover> findByAssistedPersonId(Long assistedPersonId);
    Optional<Handover> findByFromShiftId(Long fromShiftId);
    List<Handover> findOpenHandoversByShiftId(Long shiftId);
}
