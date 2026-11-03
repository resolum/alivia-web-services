package com.resolum.alivia.operations.domain.exceptions;

public class IncidentAccessDenied extends RuntimeException {
    public IncidentAccessDenied() {
        super("Only the account that reported the incident can change it.");
    }
}
