package com.resolum.alivia.assistance.interfaces.rest.resources;

import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;

public record CreateCareActivityResource(
        Long relativeId,
        Long assistedPersonId,
        CareActivityType type,
        String title,
        String description
) {}
