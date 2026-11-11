package com.resolum.alivia.assistance.interfaces.rest.resources;

public record RelativeResource(
        Long id,
        Long userId,
        Long personProfileId,
        Long businessId
) {}
