package com.resolum.alivia.operations.domain.model.aggregates;

import com.resolum.alivia.operations.domain.model.valueobjects.TechnicianOccupancy;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The window and technician a family picked for the evaluation while they finish the configuration and the
 * payment. While it lasts, no other family sees that technician in that window; when it expires or the
 * family picks another one, the window is offered again. Booking the evaluation consumes it.
 *
 * <p>A contracting request holds at most one window at a time ({@code draftId} is unique). The unique
 * technician window stops two families from holding it at once; the command service clears expired holds
 * before taking a new one, so an expired row never blocks it.</p>
 */
@Entity
@Table(name = "evaluation_slot_holds", indexes = {
        @Index(name = "idx_evaluation_slot_holds_date", columnList = "scheduled_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_evaluation_slot_holds_draft", columnNames = "draft_id"),
        @UniqueConstraint(name = "uk_evaluation_slot_holds_technician_window",
                columnNames = {"technician_id", "scheduled_date", "start_time"})
})
@Getter
public class EvaluationSlotHold extends AuditableAbstractAggregateRoot<EvaluationSlotHold> implements TechnicianOccupancy {
    /** Long enough to review the configuration and authorize the card; the payment step renews it. */
    public static final Duration DURATION = Duration.ofMinutes(30);

    @Column(name = "draft_id", nullable = false, length = 36)
    private String draftId;
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    @Column(name = "technician_id", nullable = false)
    private Long technicianId;
    @Column(name = "technician_name", nullable = false, length = 120)
    private String technicianName;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected EvaluationSlotHold() {
    }

    public EvaluationSlotHold(String draftId, LocalDate date, TimeRange window, Long technicianId,
                              String technicianName, Instant now) {
        if (draftId == null || draftId.isBlank()) throw new IllegalArgumentException("The contracting request is required.");
        this.draftId = draftId;
        moveTo(date, window, technicianId, technicianName, now);
    }

    /** Holds another window, or the same one again, for a fresh {@link #DURATION}. */
    public void moveTo(LocalDate date, TimeRange window, Long technicianId, String technicianName, Instant now) {
        if (date == null || window == null || technicianId == null || now == null) {
            throw new IllegalArgumentException("The date, the window and the technician are required.");
        }
        this.scheduledDate = date;
        this.startTime = window.start();
        this.endTime = window.end();
        this.technicianId = technicianId;
        this.technicianName = technicianName;
        this.expiresAt = now.plus(DURATION);
    }

    public boolean isActive(Instant now) {
        return expiresAt.isAfter(now);
    }

    public boolean holds(LocalDate date, TimeRange window, Long technician) {
        return scheduledDate.equals(date) && window().equals(window) && technicianId.equals(technician);
    }

    @Override
    public TimeRange window() {
        return new TimeRange(startTime, endTime);
    }

    @Override
    public boolean isAttendedBy(Long candidateTechnicianId) {
        return technicianId.equals(candidateTechnicianId);
    }
}
