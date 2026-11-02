package com.resolum.alivia.hcm.domain.model.entities;

import com.resolum.alivia.hcm.domain.model.valueobjects.ContractAction;
import com.resolum.alivia.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "contract_versions", indexes =
        @Index(name = "idx_contract_version", columnList = "contract_id,version"))
@Getter
public class ContractVersion extends AuditableModel {
    @Column(name = "contract_id", nullable = false)
    private Long contractId;
    @Column(nullable = false)
    private int version;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContractAction action;
    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> snapshot;

    protected ContractVersion() {
    }

    public ContractVersion(Long contractId, int version, ContractAction action,
                           Long actorUserId, Map<String, String> snapshot) {
        if (contractId == null || contractId <= 0 || version <= 0 || action == null
                || actorUserId == null || actorUserId <= 0 || snapshot == null) {
            throw new IllegalArgumentException("Valid contract version data is required.");
        }
        this.contractId = contractId;
        this.version = version;
        this.action = action;
        this.actorUserId = actorUserId;
        this.snapshot = Map.copyOf(snapshot);
    }
}
