package com.resolum.alivia.assistance.domain.model.aggregates;

import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.Objects;

/**
 * Relative aggregate: el familiar responsable.
 * Se registra solo en un Business y paga la suscripción.
 * Gestiona a sus personas asistidas, crea sus actividades de cuidado e invita cuidadores.
 */
@Entity
@Table(name = "relatives")
@Getter
public class Relative extends AuditableAbstractAggregateRoot<Relative> {

    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "user_id", nullable = false))
    private UserId userId;

    @Embedded
    @AttributeOverride(name = "personProfileId", column = @Column(name = "person_profile_id", nullable = false))
    private PersonProfileId personProfileId;

    @Embedded
    @AttributeOverride(name = "businessId", column = @Column(name = "business_id", nullable = false))
    private BusinessId businessId;

    protected Relative() {
    }

    public Relative(UserId userId, PersonProfileId personProfileId, BusinessId businessId) {
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null.");
        this.personProfileId = Objects.requireNonNull(personProfileId, "Person profile ID cannot be null.");
        this.businessId = Objects.requireNonNull(businessId, "Business ID cannot be null.");
    }
}
