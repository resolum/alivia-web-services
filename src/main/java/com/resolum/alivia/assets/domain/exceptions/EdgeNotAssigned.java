package com.resolum.alivia.assets.domain.exceptions;

/** The edge is not one of the edge services of the installation. */
public class EdgeNotAssigned extends RuntimeException {
    public EdgeNotAssigned(Long edgeId, Long installationId) {
        super(String.format("edge with id %d is not assigned to installation %d", edgeId, installationId));
    }
}
