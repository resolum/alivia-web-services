package com.resolum.alivia.assistance.interfaces.rest.resources;

public record ProposeSubstituteResource(
        String substituteCaregiverId,
        String substituteCaregiverName,
        String actorId
) {}
