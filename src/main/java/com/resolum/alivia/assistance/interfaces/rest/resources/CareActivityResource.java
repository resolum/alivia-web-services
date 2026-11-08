package com.resolum.alivia.assistance.interfaces.rest.resources;

import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;

public record CareActivityResource(
        Long id,
        Long assistedPersonId,
        CareActivityType type,
        String title,
        String description,
        String status
) {}
