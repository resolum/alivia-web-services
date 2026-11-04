package com.resolum.alivia.operations.domain.model.commands;

import com.resolum.alivia.operations.domain.model.valueobjects.IncidentSeverity;

/** {@code userId} is the authenticated account that reports the failure. */
public record CreateIncidentCommand(Long userId, String householdId, Long deviceId, String description,
                                    IncidentSeverity severity) {
}
