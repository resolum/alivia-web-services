package com.resolum.alivia.operations.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * One entry of the assignment history of an order: who became responsible, for which block and why.
 * The history is never edited, so it shows every technician and block the order went through.
 */
@Embeddable
public record AssignmentRecord(
        @Column(name = "technician_id", nullable = false) Long technicianId,
        @Column(name = "technician_name", nullable = false, length = 120) String technicianName,
        @Column(name = "scheduled_date", nullable = false) LocalDate scheduledDate,
        @Column(name = "start_time", nullable = false) LocalTime startTime,
        @Column(name = "end_time", nullable = false) LocalTime endTime,
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) AssignmentReason reason,
        @Column(length = 300) String note,
        @Column(name = "assigned_at", nullable = false) Instant assignedAt) {

    public AssignmentRecord {
        Objects.requireNonNull(technicianId, "The technician is required.");
        Objects.requireNonNull(technicianName, "The technician name is required.");
        Objects.requireNonNull(scheduledDate, "The block date is required.");
        Objects.requireNonNull(startTime, "The block start is required.");
        Objects.requireNonNull(endTime, "The block end is required.");
        Objects.requireNonNull(reason, "The reason is required.");
        Objects.requireNonNull(assignedAt, "The assignment time is required.");
        if (note != null && note.length() > 300) throw new IllegalArgumentException("The note cannot exceed 300 characters.");
    }
}
