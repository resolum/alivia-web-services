package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record MedicationAdministrationResource(
        String administrationId,
        String medication,
        AssistedPersonRef assistedPerson,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        ResponsibleRef responsible,
        String status,
        OutcomeResource outcome
) {
    public record AssistedPersonRef(Long id, String name) {}

    public record ResponsibleRef(String type, Long id, String name) {}

    public record OutcomeResource(String result, LocalDateTime occurredAt, String reason) {}
}
