package com.resolum.alivia.nursing.domain.model.queries;

import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.Objects;

/**
 * Query for one medication administration, on behalf of the authenticated user.
 *
 * @param userId           the authenticated user
 * @param administrationId the administration
 */
public record GetMedicationAdministrationQuery(UserId userId, MedicationAdministrationId administrationId) {

    /**
     * Validates the query.
     */
    public GetMedicationAdministrationQuery {
        Objects.requireNonNull(userId, "User cannot be null.");
        Objects.requireNonNull(administrationId, "Administration cannot be null.");
    }
}
