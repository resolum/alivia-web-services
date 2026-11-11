package com.resolum.alivia.assistance.domain.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CareShiftRepository {
    CareShift save(CareShift careShift);
    Optional<CareShift> findById(Long id);
    void delete(CareShift careShift);
    List<CareShift> findByAssistedPersonIdAndRange(Long assistedPersonId, LocalDateTime from, LocalDateTime to);
    List<CareShift> findByAssistedPersonId(Long assistedPersonId);
    List<CareShift> findConflictingShifts(Long assistedPersonId, LocalDateTime from, LocalDateTime to, Long excludeShiftId);
}
