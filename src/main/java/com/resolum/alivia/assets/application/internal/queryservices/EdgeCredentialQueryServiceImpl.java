package com.resolum.alivia.assets.application.internal.queryservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeCredentialStatusQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialStatus;
import com.resolum.alivia.assets.domain.services.EdgeCredentialQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The name keeps it apart from the bean of IAM that has the same class name.
@Service("assetsEdgeCredentialQueryService")
public class EdgeCredentialQueryServiceImpl implements EdgeCredentialQueryService {

    private final EdgeRepository edgeRepository;
    private final ExternalIamService externalIamService;

    public EdgeCredentialQueryServiceImpl(EdgeRepository edgeRepository, ExternalIamService externalIamService) {
        this.edgeRepository = edgeRepository;
        this.externalIamService = externalIamService;
    }

    @Override
    @Transactional(readOnly = true)
    public EdgeCredentialStatus handle(GetEdgeCredentialStatusQuery query) {
        if (!edgeRepository.existsById(query.edgeId())) {
            throw new EdgeNotFound(query.edgeId());
        }
        return externalIamService.getEdgeCredentialSummary(query.edgeId())
                .map(summary -> new EdgeCredentialStatus(summary.edgeId(),
                        summary.active() ? EdgeCredentialStatus.State.ACTIVE : EdgeCredentialStatus.State.REVOKED,
                        summary.issuedAt(), summary.issuedBy(), summary.ageDays(), summary.rotationRecommended(),
                        summary.lastUsedAt(), summary.revokedAt(), summary.revokedBy(), summary.revokedReason()))
                .orElseGet(() -> EdgeCredentialStatus.notIssued(query.edgeId()));
    }
}
