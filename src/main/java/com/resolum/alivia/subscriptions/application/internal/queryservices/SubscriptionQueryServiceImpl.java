package com.resolum.alivia.subscriptions.application.internal.queryservices;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.queries.GetActiveSubscriptionByUserIdQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetSubscriptionByIdQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.*;
import com.resolum.alivia.subscriptions.domain.services.SubscriptionQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.PlanRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SubscriptionQueryServiceImpl implements SubscriptionQueryService {

    private final SubscriptionRepository subscriptionRepository;
    private final ConfigurationDraftRepository draftRepository;
    private final PlanRepository planRepository;

    public SubscriptionQueryServiceImpl(SubscriptionRepository subscriptionRepository,
                                       ConfigurationDraftRepository draftRepository,
                                       PlanRepository planRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.draftRepository = draftRepository;
        this.planRepository = planRepository;
    }

    @Override
    public Optional<Subscription> handle(GetActiveSubscriptionByUserIdQuery query) {
        if (query.userId() == null) return Optional.empty();

        var existing = subscriptionRepository.findFirstByUserIdOrderByIdDesc(query.userId());
        if (existing.isPresent()) {
            return existing;
        }

        var drafts = draftRepository.findByOwnerUserIdOrderByUpdatedAtDesc(query.userId());
        if (!drafts.isEmpty()) {
            ConfigurationDraft draft = drafts.get(0);
            return Optional.of(createSubscriptionFromDraft(query.userId(), draft));
        }

        return Optional.empty();
    }

    private Subscription createSubscriptionFromDraft(Long userId, ConfigurationDraft draft) {
        var plan = planRepository.findByCodeAndStatus(draft.getPlanCode(), PlanStatus.ACTIVE)
                .orElseGet(() -> planRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("No plans configured")));

        ActuatorSelection selection = draft.actuatorQuantities() != null
                ? draft.actuatorQuantities().toSelection()
                : new ActuatorSelection(List.of(new ActuatorQuantity(ActuatorType.DOOR, 1)));

        Money monthlyPrice = plan.monthlyPriceFor(selection);
        String customerId = draft.getStripeCustomerId() != null ? draft.getStripeCustomerId() : "cus_mock_" + userId;
        String idempotencyKey = UUID.randomUUID().toString();

        Subscription subscription = new Subscription(
                new UserId(userId),
                new HouseholdId("hh-" + userId),
                plan,
                selection,
                monthlyPrice,
                customerId,
                idempotencyKey
        );
        subscription.linkDraft(draft.getId());
        subscription.linkStripeSubscription("sub_stripe_" + draft.getId());
        var now = LocalDateTime.now();
        subscription.activate(new BillingPeriod(now, now.plusMonths(1)));
        return subscriptionRepository.save(subscription);
    }

    @Override
    public Optional<Subscription> handle(GetSubscriptionByIdQuery query) {
        return subscriptionRepository.findById(query.subscriptionId());
    }
}
