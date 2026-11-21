package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.util.List;

public record MedicationScheduleResource(
        String id,
        String medication,
        String assistedPersonId,
        String caregiverId,
        String caregiverName,
        String startDate,
        String endDate,
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
