package com.resolum.alivia.operations.domain.exceptions;

import lombok.Getter;

/** The same device of the household already has an incident waiting for attention. */
@Getter
public class IncidentAlreadyOpen extends RuntimeException {
    private final Long incidentId;

    public IncidentAlreadyOpen(Long incidentId) {
        super("This device already has an open incident.");
        this.incidentId = incidentId;
    }
}
