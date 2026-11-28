package com.resolum.alivia.assistance.infrastructure.adapters;

import com.resolum.alivia.assistance.domain.model.aggregates.CareNote;
import com.resolum.alivia.assistance.domain.repositories.CareNoteRepository;
import com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories.CareNotePersistenceRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class CareNoteRepositoryImpl implements CareNoteRepository {

    private final CareNotePersistenceRepository repository;

    public CareNoteRepositoryImpl(CareNotePersistenceRepository repository) {
        this.repository = repository;
    }

    @Override
    public CareNote save(CareNote note) {
        return repository.save(note);
    }

    @Override
    public List<CareNote> findAllByAssistedPersonId(Long assistedPersonId) {
        return repository.findAllByAssistedPersonIdOrderByCreatedAtDesc(assistedPersonId);
    }

    @Override
    public List<CareNote> findAllByAssistedPersonIdBetween(Long assistedPersonId, LocalDateTime from, LocalDateTime to) {
        return repository.findAllByAssistedPersonIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                assistedPersonId, from, to);
    }
}
