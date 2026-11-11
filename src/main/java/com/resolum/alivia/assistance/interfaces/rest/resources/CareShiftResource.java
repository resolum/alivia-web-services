package com.resolum.alivia.assistance.interfaces.rest.resources;

public record CareShiftResource(
        String id,
        String caregiverId,
        String caregiverName,
        String startsAt,
        String endsAt,
        String notes
) {}
