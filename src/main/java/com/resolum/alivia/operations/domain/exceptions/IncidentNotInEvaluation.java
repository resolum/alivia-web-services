package com.resolum.alivia.operations.domain.exceptions;

/** Only an evaluated incident (En evaluación) gets its visit confirmed. */
public class IncidentNotInEvaluation extends RuntimeException {
    public IncidentNotInEvaluation() {
        super("The incident must be in evaluation before its visit is confirmed.");
    }
}
