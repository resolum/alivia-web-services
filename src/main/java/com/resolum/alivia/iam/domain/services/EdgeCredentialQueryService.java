package com.resolum.alivia.iam.domain.services;

import com.resolum.alivia.iam.domain.model.queries.GetEdgeCredentialByEdgeIdQuery;
import com.resolum.alivia.iam.domain.model.valueobjects.EdgeCredentialView;

import java.util.Optional;

/**
 * Reads the state of the credentials of the edge services.
 */
public interface EdgeCredentialQueryService {

    /**
     * Describes the credential of an edge: who issued it, how old it is and whether it was revoked.
     *
     * @param query the edge
     * @return the credential, or empty when the edge has none
     */
    Optional<EdgeCredentialView> handle(GetEdgeCredentialByEdgeIdQuery query);
}
