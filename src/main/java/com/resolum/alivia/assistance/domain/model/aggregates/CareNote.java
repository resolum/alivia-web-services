package com.resolum.alivia.assistance.domain.model.aggregates;

import com.resolum.alivia.assistance.domain.model.valueobjects.CareNoteCategory;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * A note a member of the care network leaves about an assisted person so the next one knows what happened. It is never
 * edited: a correction is a new note.
 */
@Entity
@Table(name = "assistance_care_notes",
        indexes = @Index(name = "ix_care_note_person_created", columnList = "assisted_person_id, created_at"))
@Getter
public class CareNote extends AuditableAbstractAggregateRoot<CareNote> {

    public static final int MAX_CONTENT_LENGTH = 1000;

    @Column(name = "assisted_person_id", nullable = false)
    private Long assistedPersonId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CareNoteCategory category;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    protected CareNote() {
    }

    public CareNote(Long assistedPersonId, Long authorUserId, CareNoteCategory category, String content) {
        if (assistedPersonId == null || authorUserId == null) {
            throw new IllegalArgumentException("The note needs the assisted person and its author.");
        }
        if (category == null) {
            throw new IllegalArgumentException("The category of the note is required.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("The content of the note is required.");
        }
        var text = content.strip();
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("The note has more than " + MAX_CONTENT_LENGTH + " characters.");
        }
        this.assistedPersonId = assistedPersonId;
        this.authorUserId = authorUserId;
        this.category = category;
        this.content = text;
    }
}
