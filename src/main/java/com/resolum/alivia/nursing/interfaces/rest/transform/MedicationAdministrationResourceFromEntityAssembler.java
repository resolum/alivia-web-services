package com.resolum.alivia.nursing.interfaces.rest.transform;

import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministration;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationResult;
import com.resolum.alivia.nursing.interfaces.rest.resources.MedicationAdministrationResource;

/**
 * Assembles {@link MedicationAdministrationResource} instances from medication administrations.
 */
public final class MedicationAdministrationResourceFromEntityAssembler {

    private MedicationAdministrationResourceFromEntityAssembler() {
    }

    /**
     * Converts a medication administration to its resource.
     *
     * @param administration the medication administration
     * @return the resource
     */
    public static MedicationAdministrationResource toResourceFromEntity(MedicationAdministration administration) {
        var log = administration.outcome();
        // Only ADMINISTERED and OMITTED carry outcome details; MISSED and CANCELLED show in the status.
        var result = log == null ? null : switch (log.getStatus()) {
            case COMPLETED -> MedicationResult.ADMINISTERED;
            case OMITTED -> MedicationResult.OMITTED;
            case CANCELLED, MISSED -> null;
        };
        var outcome = result == null ? null : new MedicationAdministrationResource.OutcomeResource(
                result, log.getCompletedAt(), log.getRegisteredAt(), log.getCompletedBy(),
                log.getOmissionReason(), log.getNotes(), log.isLate());
        return new MedicationAdministrationResource(
                administration.id().toString(),
                administration.medication(),
                new MedicationAdministrationResource.AssistedPersonResource(
                        administration.assistedPersonProfileId().personProfileId(),
                        administration.assistedPersonName()),
                administration.date(), administration.startTime(), administration.endTime(),
                new MedicationAdministrationResource.ResponsibleResource(
                        administration.caregiverId() == null ? CareRole.RELATIVE : CareRole.CAREGIVER,
                        administration.caregiverId(),
                        administration.responsibleName()),
                administration.status(),
                outcome);
    }
}
