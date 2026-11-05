package com.resolum.alivia.iam.domain.model.commands;

import com.resolum.alivia.iam.domain.model.valueobjects.EdgeKind;

/**
 * Issues the credential of an edge, or replaces it with a new secret when the edge already has one.
 *
 * @param edgeId   inventory id of the edge
 * @param edgeKind kind of the edge
 */
public record IssueEdgeCredentialCommand(Long edgeId, EdgeKind edgeKind) {

    public IssueEdgeCredentialCommand {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
        if (edgeKind == null) {
            throw new IllegalArgumentException("Edge kind is required.");
        }
    }
}
