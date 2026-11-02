package com.resolum.alivia.subscriptions.application.acl;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SubscriptionsContextFacadeImpl implements SubscriptionsContextFacade {
    private final ConfigurationDraftRepository drafts;
    private final SubscriptionRepository subscriptions;

    public SubscriptionsContextFacadeImpl(ConfigurationDraftRepository drafts, SubscriptionRepository subscriptions) {
        this.drafts = drafts;
        this.subscriptions = subscriptions;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isConfigurationDraftPreauthorized(String draftId) {
        if (draftId == null || draftId.isBlank()) return false;
        return drafts.findById(draftId)
                .map(draft -> draft.getPaymentStatus() == PaymentStatus.PREAUTHORIZED)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveSubscription> fetchActiveSubscriptionOfHousehold(String householdId) {
        if (householdId == null || householdId.isBlank()) return Optional.empty();
        return subscriptions.findByActiveHouseholdId(householdId.trim())
                .filter(Subscription::isActive)
                .map(subscription -> new ActiveSubscription(subscription.getId(), subscription.getUserId(),
                        subscription.getDraftId()));
    }
}
