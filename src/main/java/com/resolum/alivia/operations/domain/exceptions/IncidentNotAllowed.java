package com.resolum.alivia.operations.domain.exceptions;

public class IncidentNotAllowed extends RuntimeException {
    public IncidentNotAllowed() {
        super("Only the account with the active subscription of the household and an approved installation can report incidents.");
    }
}
