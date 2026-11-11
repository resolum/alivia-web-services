package com.resolum.alivia.assets.domain.exceptions;

/** The edge is not assigned to the registry of an active installation, so there is no table of devices for it. */
public class EdgeWithoutInstallation extends RuntimeException {
    public EdgeWithoutInstallation(Long edgeId) {
        super(String.format("edge with id %d is not assigned to an active installation", edgeId));
    }
}
