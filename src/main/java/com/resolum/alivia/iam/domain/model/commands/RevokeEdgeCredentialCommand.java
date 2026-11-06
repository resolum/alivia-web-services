package com.resolum.alivia.iam.domain.model.commands;

import com.resolum.alivia.iam.domain.model.aggregates.EdgeCredential;

/**
 * Revokes the credential of an edge. Revoking an edge without credential, or one already revoked, changes nothing.
 *
 * @param edgeId    inventory id of the edge
 * @param revokedBy account of the administrator who asks for it, or {@code null} when a change of the edge causes it
 * @param reason    why, as a short code of at most {@value EdgeCredential#REASON_MAX_LENGTH} characters
 */
public record RevokeEdgeCredentialCommand(Long edgeId, Long revokedBy, String reason) {

    public RevokeEdgeCredentialCommand {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
        if (revokedBy != null && revokedBy <= 0) {
            throw new IllegalArgumentException("Revoking account must be a positive number.");
        }
        if (reason == null || reason.isBlank() || reason.length() > EdgeCredential.REASON_MAX_LENGTH) {
            throw new IllegalArgumentException("Revocation reason is required and at most "
                    + EdgeCredential.REASON_MAX_LENGTH + " characters.");
        }
    }
}
