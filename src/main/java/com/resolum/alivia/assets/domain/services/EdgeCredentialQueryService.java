package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.queries.GetEdgeCredentialStatusQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialStatus;

/** Reads the state of the credential of an edge (kept by IAM). */
public interface EdgeCredentialQueryService {

    /**
     * Who issued the credential of the edge, how old it is and whether it was revoked.
     *
     * @return the state; {@code NOT_ISSUED} when the edge has no credential
     * @throws com.resolum.alivia.assets.domain.exceptions.EdgeNotFound when the edge does not exist
     */
    EdgeCredentialStatus handle(GetEdgeCredentialStatusQuery query);
}
