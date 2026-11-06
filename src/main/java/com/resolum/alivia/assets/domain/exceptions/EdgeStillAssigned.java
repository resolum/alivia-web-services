package com.resolum.alivia.assets.domain.exceptions;

import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;

/**
 * An operation would contradict the placement of the edge in an installation: leaving it {@code AVAILABLE} or
 * {@code RETIRED}, or changing its type (which would put it in the wrong slot). The edge has to be removed from the
 * installation first.
 */
public class EdgeStillAssigned extends RuntimeException {
    public EdgeStillAssigned(Long edgeId, AssetStatus target) {
        super(String.format("edge with id %d is still assigned to an installation and cannot become %s", edgeId, target));
    }

    private EdgeStillAssigned(String message) {
        super(message);
    }

    public static EdgeStillAssigned typeChange(Long edgeId) {
        return new EdgeStillAssigned(String.format(
                "edge with id %d is still assigned to an installation and its type cannot change; remove it first", edgeId));
    }
}
