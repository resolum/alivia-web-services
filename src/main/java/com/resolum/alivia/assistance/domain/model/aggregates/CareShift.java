package com.resolum.alivia.assistance.domain.model.aggregates;

import com.resolum.alivia.assistance.domain.model.valueobjects.CareShiftStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * CareShift aggregate: turno asignado a un cuidador para la atención de una persona asistida.
 */
@Entity
@Table(name = "care_shifts")
@Getter
public class CareShift extends AuditableAbstractAggregateRoot<CareShift> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assisted_person_id", nullable = false)
    private AssistedPerson assistedPerson;

    @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId;

    @Column(name = "caregiver_name", nullable = false, length = 150)
    private String caregiverName;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CareShiftStatus status;

    protected CareShift() {
    }

    public CareShift(AssistedPerson assistedPerson,
                     Long caregiverId,
                     String caregiverName,
                     LocalDateTime startsAt,
                     LocalDateTime endsAt,
                     String notes) {
        this.assistedPerson = Objects.requireNonNull(assistedPerson, "Assisted person cannot be null.");
        this.caregiverId = Objects.requireNonNull(caregiverId, "Caregiver ID cannot be null.");
        this.caregiverName = Objects.requireNonNull(caregiverName, "Caregiver name cannot be null.");
        this.startsAt = Objects.requireNonNull(startsAt, "Starts at cannot be null.");
        this.endsAt = Objects.requireNonNull(endsAt, "Ends at cannot be null.");
        this.notes = notes != null ? notes.trim() : "";
        this.status = CareShiftStatus.SCHEDULED;
    }

    public void update(Long caregiverId,
                       String caregiverName,
                       LocalDateTime startsAt,
                       LocalDateTime endsAt,
                       String notes) {
        if (caregiverId != null) this.caregiverId = caregiverId;
        if (caregiverName != null) this.caregiverName = caregiverName;
        if (startsAt != null) this.startsAt = startsAt;
        if (endsAt != null) this.endsAt = endsAt;
        if (notes != null) this.notes = notes.trim();
    }

    public void reassignCaregiver(Long newCaregiverId, String newCaregiverName) {
        this.caregiverId = Objects.requireNonNull(newCaregiverId, "New caregiver ID cannot be null.");
        this.caregiverName = Objects.requireNonNull(newCaregiverName, "New caregiver name cannot be null.");
    }

    public void cancel() {
        this.status = CareShiftStatus.CANCELLED;
    }

    public void complete() {
        this.status = CareShiftStatus.COMPLETED;
    }

    public boolean overlaps(LocalDateTime start, LocalDateTime end) {
        return this.startsAt.isBefore(end) && start.isBefore(this.endsAt);
    }
}
