package com.resolum.alivia.assets.domain.model.valueobjects;

import java.time.LocalDateTime;

/**
 * The state of the credential of an edge, without its secret. When the edge has no credential the state is
 * {@link State#NOT_ISSUED} and every other field is empty.
 *
 * @param issuedBy            administrator who issued it; empty for credentials that predate that record
 * @param ageDays             whole days since the current secret was issued
 * @param rotationRecommended whether it is active and old enough that it should be replaced
 * @param revokedBy           administrator who revoked it; empty when a change of the edge did
 * @param revokedReason       why it was revoked, as an {@link EdgeCredentialRevocationReason} name
 */
public record EdgeCredentialStatus(Long edgeId, State state, LocalDateTime issuedAt, Long issuedBy, Long ageDays,
                                   Boolean rotationRecommended, LocalDateTime lastUsedAt, LocalDateTime revokedAt,
                                   Long revokedBy, String revokedReason) {

    public enum State {NOT_ISSUED, ACTIVE, REVOKED}

    public static EdgeCredentialStatus notIssued(Long edgeId) {
        return new EdgeCredentialStatus(edgeId, State.NOT_ISSUED, null, null, null, null, null, null, null, null);
    }
}
