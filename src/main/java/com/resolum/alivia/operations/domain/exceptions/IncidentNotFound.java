package com.resolum.alivia.operations.domain.exceptions;

public class IncidentNotFound extends RuntimeException {
    public IncidentNotFound(Long incidentId) {
        super("The incident %d does not exist.".formatted(incidentId));
    }
}
