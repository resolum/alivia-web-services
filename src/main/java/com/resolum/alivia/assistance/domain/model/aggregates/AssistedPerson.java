package com.resolum.alivia.assistance.domain.model.aggregates;

import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

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

    protected AssistedPerson() {
    }

    public AssistedPerson(PersonProfileId personProfileId, Relative relative) {
        this.personProfileId = Objects.requireNonNull(personProfileId, "Person profile ID cannot be null.");
        this.relative = Objects.requireNonNull(relative, "Relative cannot be null.");
    }

    public boolean isManagedBy(Relative relative) {
        if (relative == null || this.relative == null) {
            return false;
        }
        return Objects.equals(this.relative.getId(), relative.getId());
    }
}
