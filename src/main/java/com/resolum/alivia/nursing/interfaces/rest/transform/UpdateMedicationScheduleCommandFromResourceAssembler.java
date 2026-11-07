package com.resolum.alivia.nursing.interfaces.rest.transform;

import com.resolum.alivia.nursing.domain.model.commands.UpdateMedicationScheduleCommand;
import com.resolum.alivia.nursing.interfaces.rest.resources.UpdateMedicationScheduleResource;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.LocalDate;

public class UpdateMedicationScheduleCommandFromResourceAssembler {

    public static UpdateMedicationScheduleCommand toCommandFromResource(
            UserId userId,
            Long medicationId,
            UpdateMedicationScheduleResource resource) {
        LocalDate effectiveFrom = resource.effectiveFrom() != null && !resource.effectiveFrom().isBlank()
                ? LocalDate.parse(resource.effectiveFrom())
                : LocalDate.now();

        return new UpdateMedicationScheduleCommand(
                userId,
                medicationId,
                resource.dose(),
                resource.times(),
                resource.frequency(),
                effectiveFrom,
                resource.indicationRef()
        );
    }
}
