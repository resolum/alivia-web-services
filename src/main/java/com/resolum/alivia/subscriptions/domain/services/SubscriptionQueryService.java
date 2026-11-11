package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.queries.GetActiveSubscriptionByUserIdQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetSubscriptionByIdQuery;

import java.util.Optional;

public interface SubscriptionQueryService {
    Optional<Subscription> handle(GetActiveSubscriptionByUserIdQuery query);
    Optional<Subscription> handle(GetSubscriptionByIdQuery query);
}
