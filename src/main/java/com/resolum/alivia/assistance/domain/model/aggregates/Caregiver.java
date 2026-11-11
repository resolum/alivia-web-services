package com.resolum.alivia.assistance.domain.model.aggregates;

import com.resolum.alivia.assistance.domain.exceptions.CaregiverAlreadyRevokedException;
import com.resolum.alivia.assistance.domain.model.events.CaregiverAccessRevokedEvent;
import com.resolum.alivia.assistance.domain.model.valueobjects.CaregiverStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.Objects;

/**
 * Caregiver aggregate: el acceso de un usuario como cuidador de una AssistedPerson.
 */
@Entity
@Table(name = "caregivers")
@Getter
public class Caregiver extends AuditableAbstractAggregateRoot<Caregiver> {

    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "user_id", nullable = false))
    private UserId userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assisted_person_id", nullable = false)
    private AssistedPerson assistedPerson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaregiverStatus status;

    protected Caregiver() {
    }

    public Caregiver(UserId userId, AssistedPerson assistedPerson) {
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null.");
        this.assistedPerson = Objects.requireNonNull(assistedPerson, "Assisted person cannot be null.");
        this.status = CaregiverStatus.ACTIVE;
    }

    public void revoke() {
        if (status == CaregiverStatus.REVOKED) {
            throw new CaregiverAlreadyRevokedException(getId());
        }
        this.status = CaregiverStatus.REVOKED;
        addDomainEvent(new CaregiverAccessRevokedEvent(getId(), assistedPerson.getId()));
    }

    public boolean isActive() {
        return status == CaregiverStatus.ACTIVE;
    }

    public boolean isCaregiverOf(AssistedPerson assistedPerson) {
        if (assistedPerson == null || this.assistedPerson == null) {
            return false;
        }
        return Objects.equals(this.assistedPerson.getId(), assistedPerson.getId());
    }
}
