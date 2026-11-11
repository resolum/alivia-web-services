package com.resolum.alivia.assistance.interfaces.rest.resources;

public record CreateCaregiverResource(
        Long assistedPersonId,
        Long caregiverUserId
) {}
