package com.resolum.alivia.subscriptions.domain.model.commands;

public record RequestPlanChangeCommand(Long subscriptionId, Long userId, String planCode) {
}
