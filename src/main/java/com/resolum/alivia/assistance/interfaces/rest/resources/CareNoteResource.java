package com.resolum.alivia.assistance.interfaces.rest.resources;

import com.resolum.alivia.assistance.domain.model.aggregates.CareNote;

import java.time.LocalDateTime;

public record CareNoteResource(Long id, Long assistedPersonId, Long authorUserId, String category, String content,
                               LocalDateTime createdAt) {

    public static CareNoteResource from(CareNote note) {
        return new CareNoteResource(note.getId(), note.getAssistedPersonId(), note.getAuthorUserId(),
                note.getCategory().name(), note.getContent(), note.getCreatedAt());
    }
}
