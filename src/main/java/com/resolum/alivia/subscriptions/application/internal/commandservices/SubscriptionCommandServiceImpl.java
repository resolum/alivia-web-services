package com.resolum.alivia.subscriptions.application.internal.commandservices;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.commands.RequestPlanChangeCommand;
import com.resolum.alivia.subscriptions.domain.model.commands.UpdateSubscriptionStatusCommand;
import com.resolum.alivia.subscriptions.domain.services.SubscriptionCommandService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubscriptionCommandServiceImpl implements SubscriptionCommandService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionCommandServiceImpl(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public Subscription handle(UpdateSubscriptionStatusCommand command) {
        var subscription = subscriptionRepository.findById(command.subscriptionId())
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + command.subscriptionId()));
        if (command.userId() != null && !subscription.getUserId().equals(command.userId())) {
            throw new IllegalArgumentException("Subscription does not belong to the user.");
        }
        subscription.updateStatus(command.status());
        return subscriptionRepository.save(subscription);
    }

    @Override
    public Subscription handle(RequestPlanChangeCommand command) {
        var subscription = subscriptionRepository.findById(command.subscriptionId())
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + command.subscriptionId()));
        if (command.userId() != null && !subscription.getUserId().equals(command.userId())) {
            throw new IllegalArgumentException("Subscription does not belong to the user.");
        }
        subscription.requestPlanChange(command.planCode());
        return subscriptionRepository.save(subscription);
    }
}
