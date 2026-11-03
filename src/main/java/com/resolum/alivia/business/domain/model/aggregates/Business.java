package com.resolum.alivia.business.domain.model.aggregates;

import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessProfileId;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
public class Business extends AuditableAbstractAggregateRoot<Business> {

    @Embedded
    private BusinessProfileId businessProfileId;

    @OneToOne(fetch = FetchType.EAGER)
    private Administrator administrator;

    /**
     * Required by JPA.
     */
    protected Business() {
    }

    public Business(BusinessProfileId businessProfileId, Administrator administrator) {
        if (businessProfileId == null) {
            throw new IllegalArgumentException("BusinessProfileId is required.");
        }
        if (administrator == null) {
            throw new IllegalArgumentException("Administrator is required.");
        }
        this.businessProfileId = businessProfileId;
        this.administrator = administrator;
    }

    public boolean isManagedBy(Administrator administrator) {
        if (this.administrator == null || administrator == null) {
            return false;
        }
        return this.administrator.getId() != null && this.administrator.getId().equals(administrator.getId());
    }
}
