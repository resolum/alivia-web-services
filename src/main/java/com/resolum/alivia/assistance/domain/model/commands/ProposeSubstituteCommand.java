package com.resolum.alivia.assistance.domain.model.commands;

public record ProposeSubstituteCommand(
        Long handoverId,
        Long substituteCaregiverId,
        String substituteCaregiverName,
        Long actorCaregiverId
) {}
