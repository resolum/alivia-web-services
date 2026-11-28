package com.resolum.alivia.operations.domain.exceptions;

/** The installation order is not linked to a contracted request with its business and household yet. */
public class InstallationContextMissing extends RuntimeException {
    public InstallationContextMissing(String message) {
        super(message);
    }
}
