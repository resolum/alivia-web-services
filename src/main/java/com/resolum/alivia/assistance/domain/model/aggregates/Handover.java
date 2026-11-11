package com.resolum.alivia.assistance.domain.model.aggregates;

import com.resolum.alivia.assistance.domain.model.valueobjects.HandoverStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Handover aggregate: relevo o traspaso de guardia entre dos turnos consecutivos.
 */
@Entity
@Table(name = "care_handovers")
@Getter
public class Handover extends AuditableAbstractAggregateRoot<Handover> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_shift_id", nullable = false)
    private CareShift fromShift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_shift_id", nullable = false)
    private CareShift toShift;

    @Column(name = "from_caregiver_id", nullable = false)
    private Long fromCaregiverId;

    @Column(name = "from_caregiver_name", nullable = false, length = 150)
    private String fromCaregiverName;

    @Column(name = "to_caregiver_id", nullable = false)
    private Long toCaregiverId;

    @Column(name = "to_caregiver_name", nullable = false, length = 150)
    private String toCaregiverName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HandoverStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "attended_tasks", columnDefinition = "TEXT")
    private String attendedTasks;

    @Column(name = "pending_tasks", columnDefinition = "TEXT")
    private String pendingTasks;

    @Column(name = "substitute", nullable = false)
    private boolean substitute;

    protected Handover() {
    }

    public Handover(CareShift fromShift,
                    CareShift toShift,
                    String attendedTasks,
                    String pendingTasks) {
        this.fromShift = Objects.requireNonNull(fromShift, "From shift cannot be null.");
        this.toShift = Objects.requireNonNull(toShift, "To shift cannot be null.");
        this.fromCaregiverId = fromShift.getCaregiverId();
        this.fromCaregiverName = fromShift.getCaregiverName();
        this.toCaregiverId = toShift.getCaregiverId();
        this.toCaregiverName = toShift.getCaregiverName();
        this.status = HandoverStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
        this.attendedTasks = attendedTasks != null ? attendedTasks : "[]";
        this.pendingTasks = pendingTasks != null ? pendingTasks : "[]";
        this.substitute = false;
    }

    public boolean isOpen() {
        return status == HandoverStatus.PENDING || status == HandoverStatus.DECLINED;
    }

    public void accept(String latestAttended, String latestPending) {
        this.status = this.substitute ? HandoverStatus.REASSIGNED : HandoverStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
        if (latestAttended != null) this.attendedTasks = latestAttended;
        if (latestPending != null) this.pendingTasks = latestPending;
    }

    public void decline() {
        this.status = HandoverStatus.DECLINED;
        this.respondedAt = LocalDateTime.now();
    }

    public void proposeSubstitute(Long substituteCaregiverId, String substituteCaregiverName) {
        this.toCaregiverId = Objects.requireNonNull(substituteCaregiverId, "Substitute caregiver ID cannot be null.");
        this.toCaregiverName = Objects.requireNonNull(substituteCaregiverName, "Substitute caregiver name cannot be null.");
        this.substitute = true;
        this.status = HandoverStatus.PENDING;
        this.respondedAt = null;
    }

    public void markEscalated() {
        this.escalatedAt = LocalDateTime.now();
    }
}
