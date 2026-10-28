package com.resolum.alivia.operations.application.internal.outboundservices.acl;

import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import org.springframework.stereotype.Service;

/** Operations' view of Subscriptions: whether the contracting draft already holds the payment. */
@Service("operationsExternalSubscriptionsService")
public class ExternalSubscriptionsService {
    private final SubscriptionsContextFacade subscriptionsContextFacade;

    public ExternalSubscriptionsService(SubscriptionsContextFacade subscriptionsContextFacade) {
        this.subscriptionsContextFacade = subscriptionsContextFacade;
    }

    public boolean isReadyForEvaluation(String draftId) {
        return subscriptionsContextFacade.isConfigurationDraftPreauthorized(draftId);
    }
}
