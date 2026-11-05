package com.resolum.alivia.assets.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The credential of an edge. The secret is shown only in this response; it is not stored and cannot be read again.
 *
 * @param credential what the edge sends as {@code Authorization: Bearer <credential>}
 */
public record IssuedEdgeCredentialResource(Long edgeId, String edgeType,
                                           @Schema(description = "Se muestra una sola vez") String credential) {

    /** Hides the secret: a record would otherwise print it. */
    @Override
    public String toString() {
        return "IssuedEdgeCredentialResource[edgeId=" + edgeId + ", edgeType=" + edgeType + ", credential=***]";
    }
}
