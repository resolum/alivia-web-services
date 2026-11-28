package com.resolum.alivia.assistance.domain.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.CareNote;

import java.time.LocalDateTime;
import java.util.List;

public interface CareNoteRepository {
    CareNote save(CareNote note);

    /** Newest first. */
    List<CareNote> findAllByAssistedPersonId(Long assistedPersonId);

    /** {@code from} included, {@code to} excluded, oldest first. */
    List<CareNote> findAllByAssistedPersonIdBetween(Long assistedPersonId, LocalDateTime from, LocalDateTime to);
}
