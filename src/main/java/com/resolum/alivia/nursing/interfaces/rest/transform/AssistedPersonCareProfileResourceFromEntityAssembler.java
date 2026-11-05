package com.resolum.alivia.nursing.interfaces.rest.transform;

import com.resolum.alivia.nursing.domain.model.aggregates.AssistedPersonCareProfile;
import com.resolum.alivia.nursing.interfaces.rest.resources.AssistedPersonCareProfileResource;

/**
 * Builds an {@link AssistedPersonCareProfileResource} from the aggregate.
 */
public final class AssistedPersonCareProfileResourceFromEntityAssembler {

    private AssistedPersonCareProfileResourceFromEntityAssembler() {
    }

    /**
     * Converts the aggregate.
     *
     * @param profile the care profile
     * @return the resource
     */
    public static AssistedPersonCareProfileResource toResourceFromEntity(AssistedPersonCareProfile profile) {
        return new AssistedPersonCareProfileResource(
                profile.getId(),
                profile.getHouseholdId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getBirthDate(),
                profile.getDocumentType() == null ? null : profile.getDocumentType().name(),
                profile.getDocumentNumber(),
                profile.getKinship().name(),
                profile.getPhoneNumber(),
                profile.getMobilityLevel().name(),
                profile.getEmergencyContactName(),
                profile.getEmergencyContactPhone(),
                profile.getConsentGivenAt());
    }
}
