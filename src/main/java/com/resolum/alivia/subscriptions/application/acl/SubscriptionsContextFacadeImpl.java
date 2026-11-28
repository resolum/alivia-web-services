package com.resolum.alivia.subscriptions.application.acl;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Plan;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.PlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Map;

@Service
public class SubscriptionsContextFacadeImpl implements SubscriptionsContextFacade {
    private final ConfigurationDraftRepository drafts;
    private final SubscriptionRepository subscriptions;
    private final PaymentAuthorizationGateway authorizations;
    /** A hiring is a request whose payment was authorized and is still held, being captured or captured. */
    private static final List<PaymentStatus> HIRED = List.of(PaymentStatus.PREAUTHORIZED,
            PaymentStatus.AWAITING_CONFIRMATION, PaymentStatus.SUCCEEDED);

    private final Clock clock;
    private final PlanRepository plans;

    public SubscriptionsContextFacadeImpl(ConfigurationDraftRepository drafts, SubscriptionRepository subscriptions,
                                          PaymentAuthorizationGateway authorizations, Clock clock) {
        this(drafts, subscriptions, authorizations, clock, null);
    }

    @Autowired
    public SubscriptionsContextFacadeImpl(ConfigurationDraftRepository drafts, SubscriptionRepository subscriptions,
                                          PaymentAuthorizationGateway authorizations, Clock clock,
                                          PlanRepository plans) {
        this.drafts = drafts;
        this.subscriptions = subscriptions;
        this.authorizations = authorizations;
        this.clock = clock;
        this.plans = plans;
    }

    /**
     * Read from the configuration drafts: the contracting flow charges there, and no {@code Payment} row is written
     * for it.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Sale> fetchSales(Instant from, Instant to, Long planId) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("The period needs a start that is not after its end.");
        }
        Map<PlanCode, Plan> planByCode = plans.findAll().stream()
                .collect(Collectors.toMap(Plan::getCode, plan -> plan, (first, second) -> first));
        return drafts.findByPaymentStatusInAndPreauthorizedAtGreaterThanEqualAndPreauthorizedAtLessThan(HIRED, from, to)
                .stream()
                .filter(draft -> planByCode.containsKey(draft.getPlanCode()))
                .filter(draft -> planId == null || planId.equals(planByCode.get(draft.getPlanCode()).getId()))
                .sorted(Comparator.comparing(ConfigurationDraft::getPreauthorizedAt))
                .map(draft -> {
                    var plan = planByCode.get(draft.getPlanCode());
                    // shortcut: the draft does not keep the authorized amount, so it is priced again with the plan as
                    // it is now; store the amount on the draft if prices start to change.
                    var quote = plan.quoteMonthly(draft.getRooms().size(), draft.actuatorQuantities().toSelection());
                    return new Sale(draft.getId(), plan.getId(), plan.getCode().name(), plan.getName(),
                            draft.getBillingCycle(), quote.totalMonthlyAmount(), quote.currency(),
                            draft.getPreauthorizedAt(), draft.getPaymentStatus() == PaymentStatus.SUCCEEDED);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countSubscriptionsByStatus() {
        var counts = new LinkedHashMap<String, Long>();
        for (var row : subscriptions.countByStatus()) counts.put(row[0].toString(), (Long) row[1]);
        return counts;
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
