package com.resolum.alivia.assets.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * The state of the credential of an edge. It never carries the secret.
 *
 * @param state               {@code NOT_ISSUED}, {@code ACTIVE} or {@code REVOKED}
 * @param issuedBy            administrator who issued it; empty for credentials that predate that record
 * @param ageDays             whole days since the current secret was issued
 * @param rotationRecommended active and older than the configured age (one year by default)
 * @param revokedBy           administrator who revoked it; empty when a change of the edge did
 * @param revokedReason       {@code REVOKED_BY_ADMINISTRATOR}, {@code EDGE_REMOVED_FROM_INSTALLATION},
 *                            {@code EDGE_REPLACED_IN_INSTALLATION}, {@code EDGE_RETIRED} or {@code EDGE_TYPE_CHANGED}
 */
public record EdgeCredentialStatusResource(Long edgeId,
                                           @Schema(allowableValues = {"NOT_ISSUED", "ACTIVE", "REVOKED"}) String state,
                                           LocalDateTime issuedAt, Long issuedBy, Long ageDays,
                                           Boolean rotationRecommended, LocalDateTime lastUsedAt,
                                           LocalDateTime revokedAt, Long revokedBy, String revokedReason) {
}
