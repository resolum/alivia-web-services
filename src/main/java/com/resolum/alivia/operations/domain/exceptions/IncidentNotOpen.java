package com.resolum.alivia.operations.domain.exceptions;

public class IncidentNotOpen extends RuntimeException {
    public IncidentNotOpen() {
        super("The incident already has a maintenance visit scheduled or was resolved.");
    }
}
