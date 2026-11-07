package com.resolum.alivia.nursing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Programación de un medicamento indicado")
public record MedicationScheduleResource(
        String id,
        String medication,
        String dose,
        String assistedPersonId,
        String caregiverId,
        String caregiverName,
        String startDate,
        String endDate,
        String status,
        List<MedicationRevisionResource> revisions,
        MedicationSuspensionResource suspension
) {
    public record MedicationRevisionResource(
            String effectiveFrom,
            String dose,
            List<String> times,
            String frequency,
            String indicationRef,
            String recordedAt,
            String recordedByName
    ) {}

    public record MedicationSuspensionResource(
            String from,
            String source,
            String recordedAt,
            String recordedByName
    ) {}
}
