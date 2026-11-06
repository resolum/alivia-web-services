package com.resolum.alivia.assets.domain.model.queries;

/**
 * Asks for the state of the credential of an edge.
 *
 * @param edgeId inventory id of the edge
 */
public record GetEdgeCredentialStatusQuery(Long edgeId) {
    public GetEdgeCredentialStatusQuery {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
    }
}
