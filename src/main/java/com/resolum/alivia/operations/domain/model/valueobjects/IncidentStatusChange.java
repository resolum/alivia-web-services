package com.resolum.alivia.operations.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;
import java.util.Objects;

/**
 * One step of the incident's timeline: the status it reached and when.
 */
@Embeddable
public record IncidentStatusChange(
        @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16) IncidentStatus status,
        @Column(name = "changed_at", nullable = false) Instant changedAt) {

    public IncidentStatusChange {
        Objects.requireNonNull(status, "The status is required.");
        Objects.requireNonNull(changedAt, "The time of the change is required.");
    }
}
