package com.resolum.alivia.operations.domain.exceptions;

public class EvaluationRequiredForInstallation extends RuntimeException {
    public EvaluationRequiredForInstallation() {
        super("The installation needs a technical evaluation of the same request, on an earlier day.");
    }
}
