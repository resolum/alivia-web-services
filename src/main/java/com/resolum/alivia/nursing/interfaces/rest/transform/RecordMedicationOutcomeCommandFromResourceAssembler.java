package com.resolum.alivia.nursing.interfaces.rest.transform;

import com.resolum.alivia.nursing.domain.model.commands.RecordMedicationOutcomeCommand;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationId;
import com.resolum.alivia.nursing.interfaces.rest.resources.RecordMedicationOutcomeResource;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Assembles {@link RecordMedicationOutcomeCommand} instances from request bodies.
 */
public final class RecordMedicationOutcomeCommandFromResourceAssembler {

    private RecordMedicationOutcomeCommandFromResourceAssembler() {
    }

    /**
     * Converts a request body to its command.
     *
     * @param userId           the authenticated user
     * @param administrationId the administration from the path
     * @param resource         the request body
     * @return the command
     */
    public static RecordMedicationOutcomeCommand toCommandFromResource(UserId userId,
                                                                       MedicationAdministrationId administrationId,
                                                                       RecordMedicationOutcomeResource resource) {
        return new RecordMedicationOutcomeCommand(userId, administrationId, resource.result(),
                resource.occurredAt(), resource.reason(), resource.notes());
    }
}
