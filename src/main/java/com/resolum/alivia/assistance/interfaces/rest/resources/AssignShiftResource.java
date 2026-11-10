package com.resolum.alivia.assistance.interfaces.rest.resources;

public record AssignShiftResource(
        String shiftId,
        Long assistedPersonId,
        String caregiverId,
        String caregiverName,
        String startsAt,
        String endsAt,
        String notes,
        Boolean allowConflict
) {}
