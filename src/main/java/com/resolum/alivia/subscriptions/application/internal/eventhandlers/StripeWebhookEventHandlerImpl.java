package com.resolum.alivia.subscriptions.application.internal.eventhandlers;

import com.resolum.alivia.subscriptions.application.outboundservices.StripeWebhookVerifier;
import com.resolum.alivia.subscriptions.domain.exceptions.StripeWebhookProcessingFailed;
import com.resolum.alivia.subscriptions.domain.model.commands.ReceiveStripeWebhookCommand;
import com.resolum.alivia.subscriptions.domain.services.StripeWebhookEventHandler;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.StripeWebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StripeWebhookEventHandlerImpl implements StripeWebhookEventHandler {
    private final StripeWebhookVerifier verifier;
    private final StripeWebhookEventRepository events;

    public StripeWebhookEventHandlerImpl(StripeWebhookVerifier verifier, StripeWebhookEventRepository events) {
        this.verifier = verifier;
        this.events = events;
    }

    @Override
    @Transactional
    public void handle(ReceiveStripeWebhookCommand command) {
        var verified = verifier.verify(command.payload(), command.signature());
        try {
            events.insertIfAbsent(verified.id(), verified.type(), command.payload());
        } catch (RuntimeException exception) {
            throw new StripeWebhookProcessingFailed(exception);
        }
    }
}
