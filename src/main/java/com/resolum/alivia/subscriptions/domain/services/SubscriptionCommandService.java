package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.commands.RequestPlanChangeCommand;
import com.resolum.alivia.subscriptions.domain.model.commands.UpdateSubscriptionStatusCommand;

public interface SubscriptionCommandService {
    Subscription handle(UpdateSubscriptionStatusCommand command);
    Subscription handle(RequestPlanChangeCommand command);
}
