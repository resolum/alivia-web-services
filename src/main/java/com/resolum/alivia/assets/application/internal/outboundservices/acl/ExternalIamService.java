package com.resolum.alivia.assets.application.internal.outboundservices.acl;

import com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialRevocationReason;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.services.EdgeTableWaitService;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Assets' view of IAM: the credential with which an edge identifies itself to the API. IAM authenticates the edge;
 * Assets decides which edge may have a credential and when it loses it.
 *
 * <p>Every change of a credential (revoked, or replaced by a new secret) also ends the wait of that edge: a request that
 * began with the old credential must not go on receiving tables. All of Assets' revocations and replacements go through
 * this class, so it is the one place that does it.</p>
 */
@Service("assetsExternalIamService")
public class ExternalIamService {
    private final IamContextFacade iamContextFacade;
    private final EdgeTableWaitService waitService;

    public ExternalIamService(IamContextFacade iamContextFacade, EdgeTableWaitService waitService) {
        this.iamContextFacade = iamContextFacade;
        this.waitService = waitService;
    }

    /** Issues the credential of the edge (or replaces its secret) and returns what the edge must send. */
    public String issueEdgeCredential(Long edgeId, EdgeType edgeType, Long issuedBy) {
        var credential = iamContextFacade.issueEdgeCredential(edgeId, switch (edgeType) {
            case MICROPHONE_EDGE -> "MICROPHONE";
            case ACTUATOR_EDGE -> "ACTUATOR";
        }, issuedBy);
        // A replaced secret ends the wait that began with the old one (a first credential has no wait to end).
        waitService.endWaitOf(edgeId);
        return credential;
    }

    /**
     * Revokes the credential of the edge; nothing happens when it has none.
     *
     * @param revokedBy the administrator who asked for it, or {@code null} when a change of the edge causes it
     */
    public void revokeEdgeCredential(Long edgeId, Long revokedBy, EdgeCredentialRevocationReason reason) {
        iamContextFacade.revokeEdgeCredential(edgeId, revokedBy, reason.name());
        waitService.endWaitOf(edgeId);
    }

    /** The state of the credential of the edge, without its secret; empty when it has none. */
    public Optional<IamContextFacade.EdgeCredentialSummary> getEdgeCredentialSummary(Long edgeId) {
        return iamContextFacade.getEdgeCredentialSummary(edgeId);
    }
}
