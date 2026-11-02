package com.resolum.alivia.operations.domain.exceptions;

public class IncidentNotScheduled extends RuntimeException {
    public IncidentNotScheduled() {
        super("Only an incident with a scheduled visit can be attended.");
    }
}
