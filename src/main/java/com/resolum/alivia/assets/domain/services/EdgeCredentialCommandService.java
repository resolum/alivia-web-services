package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.commands.IssueEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.commands.RevokeEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.IssuedEdgeCredential;

/** The credential with which an edge service identifies itself to the API (authenticated by IAM). */
public interface EdgeCredentialCommandService {

    /**
     * Issues the credential of an edge, or replaces its secret (the previous one stops working at once).
     *
     * @return the credential, with the only copy of its secret
     */
    IssuedEdgeCredential handle(IssueEdgeCredentialCommand command);

    /** Revokes the credential of an edge. It is a no-op when the edge has none or it is already revoked. */
    void handle(RevokeEdgeCredentialCommand command);
}
