package com.resolum.alivia.iam.domain.repositories;

import com.resolum.alivia.iam.domain.model.aggregates.EdgeCredential;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Persistence port for {@link EdgeCredential} aggregates.
 */
public interface EdgeCredentialRepository {

    /**
     * Finds the credential of an edge.
     *
     * @param edgeId inventory id of the edge
     * @return the credential, or empty when the edge has none
     */
    Optional<EdgeCredential> findByEdgeId(Long edgeId);

    /**
     * Finds the credential of an edge and locks it for writing, so that issuing, rotating and revoking it
     * one after another cannot overlap.
     *
     * @param edgeId inventory id of the edge
     * @return the credential, or empty when the edge has none
     */
    Optional<EdgeCredential> findByEdgeIdForUpdate(Long edgeId);

    /**
     * Stores a credential.
     *
     * @param credential the credential to store
     * @return the stored credential
     * @throws com.resolum.alivia.iam.domain.exceptions.EdgeCredentialConflictException when another request stored the
     *                                                                                    first credential of the edge
     */
    EdgeCredential save(EdgeCredential credential);

    /**
     * Records that the edge used its credential. It writes only when no use was recorded since {@code staleBefore}, in
     * a single statement that touches nothing else, so authenticating never conflicts with a rotation.
     *
     * @param edgeId      inventory id of the edge
     * @param usedAt      when the credential was used
     * @param staleBefore a recorded use at or after this moment is recent enough and is kept
     * @return {@code true} when the use was recorded
     */
    boolean recordUse(Long edgeId, LocalDateTime usedAt, LocalDateTime staleBefore);
}
