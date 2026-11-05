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

    /** The account's contracting request with an active service, if any: its home can report incidents. */
    public Optional<String> fetchActiveDraftOf(Long userId) {
        return subscriptionsContextFacade.fetchActiveDraftOfAccount(userId);
    }

    /** Returns to the family the payment held for a request that turned out not viable. */
    public boolean releasePaymentOf(String draftId) {
        return subscriptionsContextFacade.releaseConfigurationDraftPayment(draftId);
    }

    /** Captures the family's held amount after an approved installation. */
    public boolean capturePaymentOf(String draftId) {
        return subscriptionsContextFacade.captureConfigurationDraftPayment(draftId);
    }
}
