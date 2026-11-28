package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDateTime;

@Entity
@Table(name = "edge_provisioning_tokens", uniqueConstraints = @UniqueConstraint(name = "uk_edge_provisioning_edge", columnNames = "edge_id"))
@Getter
public class EdgeProvisioningToken extends AuditableAbstractAggregateRoot<EdgeProvisioningToken> {
    @Column(name="edge_id", nullable=false) private Long edgeId;
    @Column(name="token_hash", nullable=false, length=64, unique=true) private String tokenHash;
    @Column(name="expires_at", nullable=false) private LocalDateTime expiresAt;
    @Column(name="used_at") private LocalDateTime usedAt;
    @Column(name="issued_by", nullable=false) private Long issuedBy;
    protected EdgeProvisioningToken() {}
    public EdgeProvisioningToken(Long edgeId, String tokenHash, LocalDateTime expiresAt, Long issuedBy) {
        this.edgeId=edgeId; this.tokenHash=tokenHash; this.expiresAt=expiresAt; this.issuedBy=issuedBy;
    }
    public boolean consume(LocalDateTime now) {
        if (usedAt != null || !now.isBefore(expiresAt)) return false;
        usedAt = now; return true;
    }
}
