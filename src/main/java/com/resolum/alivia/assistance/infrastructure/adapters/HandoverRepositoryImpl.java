package com.resolum.alivia.assistance.infrastructure.adapters;

import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.domain.repositories.HandoverRepository;
import com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories.HandoverPersistenceRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class HandoverRepositoryImpl implements HandoverRepository {

    private final HandoverPersistenceRepository persistenceRepository;

    public HandoverRepositoryImpl(HandoverPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Handover save(Handover handover) {
        return persistenceRepository.save(handover);
    }

    @Override
    public Optional<Handover> findById(Long id) {
        return persistenceRepository.findById(id);
    }

    @Override
    public List<Handover> findByAssistedPersonId(Long assistedPersonId) {
        return persistenceRepository.findByAssistedPersonId(assistedPersonId);
    }

    @Override
    public Optional<Handover> findByFromShiftId(Long fromShiftId) {
        return persistenceRepository.findByFromShiftId(fromShiftId);
    }

    @Override
    public List<Handover> findOpenHandoversByShiftId(Long shiftId) {
        return persistenceRepository.findOpenHandoversByShiftId(shiftId);
    }
}
