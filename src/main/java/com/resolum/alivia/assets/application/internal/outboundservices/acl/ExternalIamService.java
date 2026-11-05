package com.resolum.alivia.assets.application.internal.outboundservices.acl;

import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import org.springframework.stereotype.Service;

/**
 * Assets' view of IAM: the credential with which an edge identifies itself to the API. IAM authenticates the edge;
 * Assets decides which edge may have a credential and when it loses it.
 */
@Service("assetsExternalIamService")
public class ExternalIamService {
    private final IamContextFacade iamContextFacade;

    public ExternalIamService(IamContextFacade iamContextFacade) {
        this.iamContextFacade = iamContextFacade;
    }

    /** Issues the credential of the edge (or replaces its secret) and returns what the edge must send. */
    public String issueEdgeCredential(Long edgeId, EdgeType edgeType) {
        return iamContextFacade.issueEdgeCredential(edgeId, switch (edgeType) {
            case MICROPHONE_EDGE -> "MICROPHONE";
            case ACTUATOR_EDGE -> "ACTUATOR";
        });
    }

    /** Revokes the credential of the edge; nothing happens when it has none. */
    public void revokeEdgeCredential(Long edgeId) {
        iamContextFacade.revokeEdgeCredential(edgeId);
    }
}
