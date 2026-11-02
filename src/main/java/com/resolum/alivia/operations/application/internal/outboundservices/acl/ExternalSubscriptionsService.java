package com.resolum.alivia.operations.application.internal.outboundservices.acl;

import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Operations' view of Subscriptions: the contracting draft and the subscription in force of a household. */
@Service("operationsExternalSubscriptionsService")
public class ExternalSubscriptionsService {
    private final SubscriptionsContextFacade subscriptionsContextFacade;

    public ExternalSubscriptionsService(SubscriptionsContextFacade subscriptionsContextFacade) {
        this.subscriptionsContextFacade = subscriptionsContextFacade;
    }

    public boolean isReadyForEvaluation(String draftId) {
        return subscriptionsContextFacade.isConfigurationDraftPreauthorized(draftId);
    }

    /**
     * Contracting draft of the household's active subscription when {@code userId} holds it; empty when the
     * household has no active subscription, it belongs to another account or it was not linked to a draft.
     */
    public Optional<String> fetchSubscribedDraft(String householdId, Long userId) {
        return subscriptionsContextFacade.fetchActiveSubscriptionOfHousehold(householdId)
                .filter(subscription -> subscription.userId().equals(userId))
                .map(subscription -> subscription.draftId())
                .filter(draftId -> !draftId.isBlank());
    }
}
