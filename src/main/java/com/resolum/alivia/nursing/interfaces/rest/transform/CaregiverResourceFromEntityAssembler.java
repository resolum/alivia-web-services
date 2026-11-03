package com.resolum.alivia.nursing.interfaces.rest.transform;

import com.resolum.alivia.nursing.domain.model.valueobjects.CaregiverSummary;
import com.resolum.alivia.nursing.interfaces.rest.resources.CaregiverResource;

/**
 * Assembles {@link CaregiverResource} instances from caregiver summaries.
 */
public final class CaregiverResourceFromEntityAssembler {

    private CaregiverResourceFromEntityAssembler() {
    }

    /**
     * Converts a caregiver summary to its resource.
     *
     * @param summary the caregiver summary
     * @return the resource
     */
    public static CaregiverResource toResourceFromEntity(CaregiverSummary summary) {
        return new CaregiverResource(summary.userId().userId(), summary.personProfileId().personProfileId(),
                summary.assistedPersonProfileId().personProfileId(), summary.fullName(), summary.email());
    }
}
