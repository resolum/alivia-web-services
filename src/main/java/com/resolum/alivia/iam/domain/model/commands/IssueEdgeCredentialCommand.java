package com.resolum.alivia.iam.domain.model.commands;

import com.resolum.alivia.iam.domain.model.valueobjects.EdgeKind;

/**
 * Issues the credential of an edge, or replaces it with a new secret when the edge already has one.
 *
 * @param edgeId   inventory id of the edge
 * @param edgeKind kind of the edge
 * @param issuedBy account of the administrator who asks for it
 */
public record IssueEdgeCredentialCommand(Long edgeId, EdgeKind edgeKind, Long issuedBy) {

    public IssueEdgeCredentialCommand {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
        if (edgeKind == null) {
            throw new IllegalArgumentException("Edge kind is required.");
        }
        if (issuedBy == null || issuedBy <= 0) {
            throw new IllegalArgumentException("Issuing account must be a positive number.");
        }
    }
}
