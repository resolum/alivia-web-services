package com.resolum.alivia.assets.domain.model.valueobjects;

/**
 * A credential that was just issued for an edge, with the only copy of its secret.
 *
 * @param edgeId     edge the credential belongs to
 * @param edgeType   type of that edge
 * @param credential what the edge sends as bearer token
 */
public record IssuedEdgeCredential(Long edgeId, EdgeType edgeType, String credential) {

    /** Hides the secret: a record would otherwise print it. */
    @Override
    public String toString() {
        return "IssuedEdgeCredential[edgeId=" + edgeId + ", edgeType=" + edgeType + ", credential=***]";
    }
}
