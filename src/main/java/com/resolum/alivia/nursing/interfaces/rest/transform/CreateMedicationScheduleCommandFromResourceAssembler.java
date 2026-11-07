package com.resolum.alivia.nursing.interfaces.rest.transform;

import com.resolum.alivia.nursing.domain.model.commands.CreateMedicationScheduleCommand;
import com.resolum.alivia.nursing.interfaces.rest.resources.CreateMedicationScheduleResource;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.LocalDate;

public class CreateMedicationScheduleCommandFromResourceAssembler {

    public static CreateMedicationScheduleCommand toCommandFromResource(
            UserId userId,
            Long assistedPersonId,
            CreateMedicationScheduleResource resource) {
        Long targetAssistedPersonId = assistedPersonId != null ? assistedPersonId : resource.assistedPersonId();
        if (targetAssistedPersonId == null) {
            throw new IllegalArgumentException("Assisted person id is required");
        }

        LocalDate startDate = resource.startDate() != null && !resource.startDate().isBlank()
                ? LocalDate.parse(resource.startDate())
                : LocalDate.now();

        LocalDate endDate = resource.endDate() != null && !resource.endDate().isBlank()
                ? LocalDate.parse(resource.endDate())
                : null;

        return new CreateMedicationScheduleCommand(
                userId,
                new PersonProfileId(targetAssistedPersonId),
                resource.medication(),
                resource.dose(),
                resource.times(),
                resource.frequency(),
                startDate,
                endDate,
                resource.caregiverId(),
                resource.indicationRef()
        );
    }
}
