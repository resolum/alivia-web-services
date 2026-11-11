package com.resolum.alivia.iam.domain.model.valueobjects;

import java.time.LocalDateTime;

/**
 * What can be known about the credential of an edge without its secret.
 *
 * @param edgeId              inventory id of the edge
 * @param edgeKind            kind of the edge
 * @param active              whether it can still authenticate the edge
 * @param issuedAt            when the current secret was issued or rotated
 * @param issuedBy            administrator who issued it; {@code null} for credentials that existed before it was recorded
 * @param ageDays             whole days since the current secret was issued
 * @param rotationRecommended whether it is active and older than the configured age after which it should be replaced
 * @param lastUsedAt          last time the edge used it (recorded at most once a minute); {@code null} if never
 * @param revokedAt           when it was revoked; {@code null} while active
 * @param revokedBy           administrator who revoked it; {@code null} when a change of the edge did or while active
 * @param revokedReason       why it was revoked; {@code null} while active
 */
public record EdgeCredentialView(Long edgeId, EdgeKind edgeKind, boolean active, LocalDateTime issuedAt, Long issuedBy,
                                 long ageDays, boolean rotationRecommended, LocalDateTime lastUsedAt,
                                 LocalDateTime revokedAt, Long revokedBy, String revokedReason) {
}
