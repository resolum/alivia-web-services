package com.resolum.alivia.iam.domain.model.queries;

/**
 * Looks up the credential of an edge.
 *
 * @param edgeId inventory id of the edge
 */
public record GetEdgeCredentialByEdgeIdQuery(Long edgeId) {
    public GetEdgeCredentialByEdgeIdQuery {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
    }
}
