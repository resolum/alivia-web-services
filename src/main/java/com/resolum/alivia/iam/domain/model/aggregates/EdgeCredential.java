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

    public static final int REASON_MAX_LENGTH = 40;

    @Column(name = "edge_id", nullable = false, updatable = false)
    private Long edgeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "edge_kind", nullable = false, length = 16)
    private EdgeKind edgeKind;

    @Getter(AccessLevel.NONE)
    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    /** When the current secret was issued or rotated. */
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    /** Account of the administrator who issued the current secret. */
    @Column(name = "issued_by")
    private Long issuedBy;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /** Administrator who revoked it; empty when a change of the edge did, which {@code revokedReason} tells. */
    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_reason", length = REASON_MAX_LENGTH)
    private String revokedReason;

    protected EdgeCredential() {
    }

    private EdgeCredential(Long edgeId, EdgeKind edgeKind, String secretHash, Long issuedBy, LocalDateTime issuedAt) {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
        this.edgeId = edgeId;
        this.edgeKind = requireKind(edgeKind);
        this.secretHash = requireHash(secretHash);
        this.issuedBy = requireAccount(issuedBy);
        this.issuedAt = requireMoment(issuedAt);
    }

    /** Creates the credential of an edge that has none. */
    public static EdgeCredential issue(Long edgeId, EdgeKind edgeKind, String secretHash, Long issuedBy,
                                       LocalDateTime issuedAt) {
        return new EdgeCredential(edgeId, edgeKind, secretHash, issuedBy, issuedAt);
    }

    /**
     * Replaces the secret. The previous one stops working at once, and a revoked credential becomes valid again with
     * the new secret.
     */
    public EdgeCredential rotate(EdgeKind edgeKind, String secretHash, Long issuedBy, LocalDateTime issuedAt) {
        this.edgeKind = requireKind(edgeKind);
        this.secretHash = requireHash(secretHash);
        this.issuedBy = requireAccount(issuedBy);
        this.issuedAt = requireMoment(issuedAt);
        this.revokedAt = null;
        this.revokedBy = null;
        this.revokedReason = null;
        this.lastUsedAt = null;
        return this;
    }

    /**
     * Stops the credential from authenticating.
     *
     * @param revokedBy administrator who asked for it, or {@code null} when a change of the edge caused it
     * @param reason    why, as a short code (for example {@code EDGE_RETIRED})
     * @return {@code true} when it was active; {@code false} when it was already revoked (nothing changes)
     */
    public boolean revoke(LocalDateTime at, Long revokedBy, String reason) {
        if (revokedAt != null) {
            return false;
        }
        if (reason == null || reason.isBlank() || reason.length() > REASON_MAX_LENGTH) {
            throw new IllegalArgumentException("Revocation reason is required and at most " + REASON_MAX_LENGTH + " characters.");
        }
        if (revokedBy != null && revokedBy <= 0) {
            throw new IllegalArgumentException("Revoking account must be a positive number.");
        }
        this.revokedAt = requireMoment(at);
        this.revokedBy = revokedBy;
        this.revokedReason = reason;
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

    private static Long requireAccount(Long account) {
        if (account == null || account <= 0) {
            throw new IllegalArgumentException("Issuing account must be a positive number.");
        }
        return account;
    }

    private static LocalDateTime requireMoment(LocalDateTime moment) {
        if (moment == null) {
            throw new IllegalArgumentException("The moment is required.");
        }
        return moment;
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
