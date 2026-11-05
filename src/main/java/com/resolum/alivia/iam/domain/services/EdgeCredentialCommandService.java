package com.resolum.alivia.iam.domain.services;

import com.resolum.alivia.iam.domain.model.commands.AuthenticateEdgeCommand;
import com.resolum.alivia.iam.domain.model.commands.IssueEdgeCredentialCommand;
import com.resolum.alivia.iam.domain.model.commands.RevokeEdgeCredentialCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AuthenticatedEdge;
import com.resolum.alivia.iam.domain.model.valueobjects.IssuedEdgeCredential;

import java.util.Optional;

/**
 * Credentials with which the edge services of a household identify themselves.
 */
public interface EdgeCredentialCommandService {

    /**
     * Issues the credential of an edge, or replaces its secret when it already has one.
     *
     * @param command the edge and its kind
     * @return the credential, with the only copy of its secret
     */
    IssuedEdgeCredential handle(IssueEdgeCredentialCommand command);

    /**
     * Revokes the credential of an edge. It is a no-op when the edge has none or it is already revoked.
     *
     * @param command the edge
     */
    void handle(RevokeEdgeCredentialCommand command);

    /**
     * Identifies the edge that owns a presented credential.
     *
     * @param command the credential as the edge sent it
     * @return the edge, or empty when the credential is malformed, unknown, wrong or revoked (all alike, so the
     *         caller learns nothing)
     */
    Optional<AuthenticatedEdge> handle(AuthenticateEdgeCommand command);
}
