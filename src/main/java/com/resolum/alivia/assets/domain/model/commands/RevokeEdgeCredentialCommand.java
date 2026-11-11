package com.resolum.alivia.assets.domain.model.commands;

/**
 * Revokes the credential of an edge. Nothing happens when the edge has none or it is already revoked.
 *
 * @param revokedBy account of the administrator who asks for it
 */
public record RevokeEdgeCredentialCommand(Long edgeId, Long revokedBy) {

    public RevokeEdgeCredentialCommand {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
        if (revokedBy == null || revokedBy <= 0) {
            throw new IllegalArgumentException("Revoking account must be a positive number.");
        }
    }
}
