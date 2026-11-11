package com.resolum.alivia.subscriptions.domain.model.commands;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.SubscriptionStatus;

public record UpdateSubscriptionStatusCommand(Long subscriptionId, Long userId, SubscriptionStatus status) {
}
