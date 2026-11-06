package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.EdgeRetired;
import com.resolum.alivia.assets.domain.model.commands.IssueEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.commands.RevokeEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialRevocationReason;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.IssuedEdgeCredential;
import com.resolum.alivia.assets.domain.services.EdgeCredentialCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides which edge may have a credential; IAM generates, stores and checks it. The edge is read under lock, so its
 * type cannot change (which revokes the credential) between reading it and storing the credential of that type.
 */
// IAM has a service with the same class name; the explicit name keeps both beans apart.
@Service("assetsEdgeCredentialCommandService")
public class EdgeCredentialCommandServiceImpl implements EdgeCredentialCommandService {
    private final EdgeRepository edgeRepository;
    private final ExternalIamService externalIamService;

    public EdgeCredentialCommandServiceImpl(EdgeRepository edgeRepository, ExternalIamService externalIamService) {
        this.edgeRepository = edgeRepository;
        this.externalIamService = externalIamService;
    }

    @Override
    @Transactional
    public IssuedEdgeCredential handle(IssueEdgeCredentialCommand command) {
        var edge = edgeRepository.findByIdForUpdate(command.edgeId())
                .orElseThrow(() -> new EdgeNotFound(command.edgeId()));
        if (edge.getAssetStatus() == AssetStatus.RETIRED) {
            throw new EdgeRetired(edge.getId());
        }
        var credential = externalIamService.issueEdgeCredential(edge.getId(), edge.getEdgeType(), command.issuedBy());
        return new IssuedEdgeCredential(edge.getId(), edge.getEdgeType(), credential);
    }

    @Override
    @Transactional
    public void handle(RevokeEdgeCredentialCommand command) {
        if (!edgeRepository.existsById(command.edgeId())) {
            throw new EdgeNotFound(command.edgeId());
        }
        externalIamService.revokeEdgeCredential(command.edgeId(), command.revokedBy(),
                EdgeCredentialRevocationReason.REVOKED_BY_ADMINISTRATOR);
    }
}
