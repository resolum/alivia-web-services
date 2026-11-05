package com.resolum.alivia.assets.domain.model.commands;

/** Revokes the credential of an edge. Nothing happens when the edge has none or it is already revoked. */
public record RevokeEdgeCredentialCommand(Long edgeId) {

    public RevokeEdgeCredentialCommand {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
    }
}
