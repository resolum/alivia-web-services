package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.commands.ReceiveStripeWebhookCommand;

public interface StripeWebhookEventHandler {
    void handle(ReceiveStripeWebhookCommand command);
}
