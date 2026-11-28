package com.resolum.alivia.assistance.domain.model.commands;

import com.resolum.alivia.assistance.domain.model.valueobjects.CareNoteCategory;

/** The author is the authenticated user, never a field of the request. */
public record CreateCareNoteCommand(Long assistedPersonId, Long authorUserId, CareNoteCategory category,
                                    String content) {
}
