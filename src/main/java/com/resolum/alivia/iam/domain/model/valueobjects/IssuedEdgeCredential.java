package com.resolum.alivia.iam.domain.model.valueobjects;

/**
 * A credential that was just issued, with the only copy of its secret.
 *
 * @param edgeId     inventory id of the edge
 * @param edgeKind   kind of the edge
 * @param credential what the edge sends as bearer token: {@code <edgeId>.<secret>}
 */
public record IssuedEdgeCredential(Long edgeId, EdgeKind edgeKind, String credential) {

    /** Hides the secret: a record would otherwise print it. */
    @Override
    public String toString() {
        return "IssuedEdgeCredential[edgeId=" + edgeId + ", edgeKind=" + edgeKind + ", credential=***]";
    }
}
