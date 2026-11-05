package com.resolum.alivia.assets.domain.model.commands;

/** Issues the credential of an edge, or replaces its secret when it already has one. */
public record IssueEdgeCredentialCommand(Long edgeId) {

    public IssueEdgeCredentialCommand {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
    }
}
