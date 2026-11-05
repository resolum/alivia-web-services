package com.resolum.alivia.iam.domain.model.commands;

/**
 * Revokes the credential of an edge. Revoking an edge without credential, or one already revoked, changes nothing.
 *
 * @param edgeId inventory id of the edge
 */
public record RevokeEdgeCredentialCommand(Long edgeId) {

    public RevokeEdgeCredentialCommand {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
    }
}
