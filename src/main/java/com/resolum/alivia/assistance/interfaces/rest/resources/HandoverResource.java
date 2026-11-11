package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.util.List;

public record HandoverResource(
        String id,
        String fromShiftId,
        String toShiftId,
        String fromCaregiverId,
        String fromCaregiverName,
        String toCaregiverId,
        String toCaregiverName,
        String status,
        String requestedAt,
        String respondedAt,
        String escalatedAt,
        List<String> attended,
        List<String> pending,
        boolean substitute
) {}
