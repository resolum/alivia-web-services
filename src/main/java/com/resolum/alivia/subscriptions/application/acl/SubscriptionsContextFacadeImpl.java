package com.resolum.alivia.subscriptions.application.acl;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Map;

@Service
public class SubscriptionsContextFacadeImpl implements SubscriptionsContextFacade {
    private final ConfigurationDraftRepository drafts;
    private final SubscriptionRepository subscriptions;
    private final PaymentAuthorizationGateway authorizations;
    private final Clock clock;

    public SubscriptionsContextFacadeImpl(ConfigurationDraftRepository drafts, SubscriptionRepository subscriptions,
                                          PaymentAuthorizationGateway authorizations, Clock clock) {
        this.drafts = drafts;
        this.subscriptions = subscriptions;
        this.authorizations = authorizations;
        this.clock = clock;
    }

    /**
     * Cancels the manual-capture payment intent of the draft, which returns the held amount to the family's
     * card, and marks the payment as canceled.
     */
    @Override
    @Transactional
    public boolean releaseConfigurationDraftPayment(String draftId) {
        if (draftId == null || draftId.isBlank()) return false;
        var draft = drafts.findById(draftId).orElse(null);
        if (draft == null || draft.getPaymentStatus() != PaymentStatus.PREAUTHORIZED
                || draft.getStripePaymentIntentId() == null) {
            return false;
        }
        authorizations.cancel(new StripePaymentIntentId(draft.getStripePaymentIntentId()));
        draft.markPreauthorizationCanceled();
        drafts.save(draft);
        return true;
    }

    @Override
    @Transactional
    public boolean captureConfigurationDraftPayment(String draftId) {
        if (draftId == null || draftId.isBlank()) return false;
        var draft = drafts.findById(draftId).orElse(null);
        if (draft == null || draft.getStripePaymentIntentId() == null) return false;
        if (draft.getPaymentStatus() == PaymentStatus.SUCCEEDED) return true;
        if (draft.getPaymentStatus() != PaymentStatus.PREAUTHORIZED
                && draft.getPaymentStatus() != PaymentStatus.AWAITING_CONFIRMATION) return false;
        // A repeated capture of an intent already charged is idempotent at Stripe and answers succeeded again,
        // which also settles requests that were waiting for a webhook that never arrived (local environments).
        var captured = authorizations.capture(new StripePaymentIntentId(draft.getStripePaymentIntentId()));
        if (captured == PaymentStatus.SUCCEEDED) draft.confirmCapturedPayment();
        else draft.markCaptureRequested();
        drafts.save(draft);
        return true;
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

    @Override
    @Transactional(readOnly = true)
    public Optional<String> fetchActiveDraftOfAccount(Long userId) {
        if (userId == null) return Optional.empty();
        return drafts.findByOwnerUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(draft -> draft.getPaymentStatus() == PaymentStatus.SUCCEEDED)
                .map(ConfigurationDraft::getId)
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContractedActuators> fetchContractedActuatorsOfHousehold(String householdId) {
        if (householdId == null || householdId.isBlank()) return Optional.empty();
        return subscriptions.findByActiveHouseholdId(householdId.trim())
                .filter(Subscription::isActive)
                .map(subscription -> new ContractedActuators(subscription.getActuators().entrySet().stream()
                        .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue))));
    }
}
