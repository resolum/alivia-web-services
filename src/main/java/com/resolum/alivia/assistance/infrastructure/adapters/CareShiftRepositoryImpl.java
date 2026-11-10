package com.resolum.alivia.assistance.infrastructure.adapters;

import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.repositories.CareShiftRepository;
import com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories.CareShiftPersistenceRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class CareShiftRepositoryImpl implements CareShiftRepository {

    private final CareShiftPersistenceRepository persistenceRepository;

    public CareShiftRepositoryImpl(CareShiftPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public CareShift save(CareShift careShift) {
        return persistenceRepository.save(careShift);
    }

    @Override
    public Optional<CareShift> findById(Long id) {
        return persistenceRepository.findById(id);
    }

    @Override
    public void delete(CareShift careShift) {
        persistenceRepository.delete(careShift);
    }

    @Override
    public List<CareShift> findByAssistedPersonIdAndRange(Long assistedPersonId, LocalDateTime from, LocalDateTime to) {
        return persistenceRepository.findByAssistedPersonIdAndRange(assistedPersonId, from, to);
    }

    @Override
    public List<CareShift> findByAssistedPersonId(Long assistedPersonId) {
        return persistenceRepository.findByAssistedPersonId(assistedPersonId);
    }

    @Override
    public List<CareShift> findConflictingShifts(Long assistedPersonId, LocalDateTime from, LocalDateTime to, Long excludeShiftId) {
        return persistenceRepository.findConflictingShifts(assistedPersonId, from, to, excludeShiftId);
    }
}
