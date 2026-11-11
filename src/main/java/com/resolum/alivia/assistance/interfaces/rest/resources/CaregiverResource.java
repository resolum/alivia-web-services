package com.resolum.alivia.assistance.interfaces.rest.resources;

public record CaregiverResource(
        Long id,
        Long userId,
        Long assistedPersonId,
        String status
) {}
