package com.resolum.alivia.assistance.domain.model.aggregates;

import com.resolum.alivia.assistance.domain.model.valueobjects.EmergencyContact;
import com.resolum.alivia.assistance.domain.model.valueobjects.Kinship;
import com.resolum.alivia.assistance.domain.model.valueobjects.MobilityLevel;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
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

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AssistedPerson aggregate: la persona que recibe el cuidado.
 * Pertenece a un solo Relative y no se da de baja.
 */
@Entity
@Table(name = "assisted_persons")
@Getter
public class AssistedPerson extends AuditableAbstractAggregateRoot<AssistedPerson> {

    @Embedded
    @AttributeOverride(name = "personProfileId", column = @Column(name = "person_profile_id", nullable = false))
    private PersonProfileId personProfileId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relative_id", nullable = false)
    private Relative relative;

    @Column(name = "household_id")
    private Long householdId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kinship", length = 20)
    private Kinship kinship;

    @Enumerated(EnumType.STRING)
    @Column(name = "mobility_level", length = 20)
    private MobilityLevel mobilityLevel;

    @Embedded
    private EmergencyContact emergencyContact;

    @Column(name = "consent_given_at")
    private LocalDateTime consentGivenAt;

    protected AssistedPerson() {
    }

    public AssistedPerson(PersonProfileId personProfileId, Relative relative) {
        this(personProfileId, relative, null, null, null, null, null);
    }

    public AssistedPerson(PersonProfileId personProfileId, Relative relative, Long householdId,
                          Kinship kinship, MobilityLevel mobilityLevel,
                          EmergencyContact emergencyContact, LocalDateTime consentGivenAt) {
        this.personProfileId = Objects.requireNonNull(personProfileId, "Person profile ID cannot be null.");
        this.relative = Objects.requireNonNull(relative, "Relative cannot be null.");
        this.householdId = householdId;
        this.kinship = kinship;
        this.mobilityLevel = mobilityLevel;
        this.emergencyContact = emergencyContact;
        this.consentGivenAt = consentGivenAt;
    }

    public void updateCareDetails(Long householdId, Kinship kinship, MobilityLevel mobilityLevel,
                                  EmergencyContact emergencyContact) {
        if (householdId != null) this.householdId = householdId;
        if (kinship != null) this.kinship = kinship;
        if (mobilityLevel != null) this.mobilityLevel = mobilityLevel;
        if (emergencyContact != null) this.emergencyContact = emergencyContact;
    }

    public boolean isManagedBy(Relative relative) {
        if (relative == null || this.relative == null) {
            return false;
        }
        return Objects.equals(this.relative.getId(), relative.getId());
    }
}
