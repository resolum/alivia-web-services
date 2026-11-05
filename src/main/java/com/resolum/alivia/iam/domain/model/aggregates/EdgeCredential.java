package com.resolum.alivia.iam.domain.model.aggregates;

import com.resolum.alivia.iam.domain.model.valueobjects.EdgeKind;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * The secret with which one edge service of a household identifies itself to the API.
 *
 * <p>There is one credential per edge. Only the SHA-256 hash of the secret is stored: the plain secret is shown once,
 * when the credential is issued or rotated. The edge is identified by its inventory id ({@code edgeId}), which
 * belongs to the {@code assets} context, so this aggregate keeps it as a plain number.</p>
 *
 * <p>A revoked credential stays stored (to keep the trace) and authenticates nobody until a new secret is issued.</p>
 */
@Entity
@Table(name = "edge_credentials", uniqueConstraints =
        @UniqueConstraint(name = "uk_edge_credentials_edge", columnNames = "edge_id"))
@Getter
public class EdgeCredential extends AuditableAbstractAggregateRoot<EdgeCredential> {

    @Column(name = "edge_id", nullable = false, updatable = false)
    private Long edgeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "edge_kind", nullable = false, length = 16)
    private EdgeKind edgeKind;

    @Getter(AccessLevel.NONE)
    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected EdgeCredential() {
    }

    private EdgeCredential(Long edgeId, EdgeKind edgeKind, String secretHash) {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
        this.edgeId = edgeId;
        this.edgeKind = requireKind(edgeKind);
        this.secretHash = requireHash(secretHash);
    }

    /** Creates the credential of an edge that has none. */
    public static EdgeCredential issue(Long edgeId, EdgeKind edgeKind, String secretHash) {
        return new EdgeCredential(edgeId, edgeKind, secretHash);
    }

    /**
     * Replaces the secret. The previous one stops working at once, and a revoked credential becomes valid again with
     * the new secret.
     */
    public EdgeCredential rotate(EdgeKind edgeKind, String secretHash) {
        this.edgeKind = requireKind(edgeKind);
        this.secretHash = requireHash(secretHash);
        this.revokedAt = null;
        this.lastUsedAt = null;
        return this;
    }

    /**
     * Stops the credential from authenticating.
     *
     * @return {@code true} when it was active; {@code false} when it was already revoked (nothing changes)
     */
    public boolean revoke(LocalDateTime at) {
        if (revokedAt != null) {
            return false;
        }
        this.revokedAt = at;
        return true;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    /** Compares in constant time, so the time taken does not reveal how much of the hash matched. */
    public boolean matches(String candidateHash) {
        return candidateHash != null && MessageDigest.isEqual(
                secretHash.getBytes(StandardCharsets.US_ASCII), candidateHash.getBytes(StandardCharsets.US_ASCII));
    }

    private static EdgeKind requireKind(EdgeKind edgeKind) {
        if (edgeKind == null) {
            throw new IllegalArgumentException("Edge kind is required.");
        }
        return edgeKind;
    }

    private static String requireHash(String secretHash) {
        if (secretHash == null || secretHash.isBlank()) {
            throw new IllegalArgumentException("Secret hash is required.");
        }
        return secretHash;
    }
}
