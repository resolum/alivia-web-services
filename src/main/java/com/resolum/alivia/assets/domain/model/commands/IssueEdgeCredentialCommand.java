package com.resolum.alivia.assets.domain.model.commands;

/**
 * Issues the credential of an edge, or replaces its secret when it already has one.
 *
 * @param issuedBy account of the administrator who asks for it
 */
public record IssueEdgeCredentialCommand(Long edgeId, Long issuedBy) {

    public IssueEdgeCredentialCommand {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
        if (issuedBy == null || issuedBy <= 0) {
            throw new IllegalArgumentException("Issuing account must be a positive number.");
        }
    }
}
