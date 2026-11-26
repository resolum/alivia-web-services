package com.resolum.alivia.tracking.domain.exceptions;

/** The edge that reports is not assigned to an active installation, so there is no home its reports are about. */
public class EdgeNotInInstallation extends RuntimeException {
    public EdgeNotInInstallation(Long edgeId) {
        super("The edge " + edgeId + " is not assigned to an active installation.");
    }
}
