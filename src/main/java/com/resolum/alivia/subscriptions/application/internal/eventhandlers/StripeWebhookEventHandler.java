package com.resolum.alivia.subscriptions.application.internal.eventhandlers;

import com.resolum.alivia.subscriptions.application.outboundservices.StripeWebhookVerifier;
import com.resolum.alivia.subscriptions.domain.exceptions.StripeWebhookProcessingFailed;
import com.resolum.alivia.subscriptions.domain.model.commands.ReceiveStripeWebhookCommand;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.StripeWebhookEventRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StripeWebhookEventHandler {
    private final StripeWebhookVerifier verifier;
    private final StripeWebhookEventRepository events;
    private final ConfigurationDraftRepository drafts;

    public StripeWebhookEventHandler(StripeWebhookVerifier verifier, StripeWebhookEventRepository events,
                                     ConfigurationDraftRepository drafts) {
        this.verifier = verifier;
        this.events = events;
        this.drafts = drafts;
    }

    @Transactional
    public void handle(ReceiveStripeWebhookCommand command) {
        var verified = verifier.verify(command.payload(), command.signature());
        try {
            if (events.insertIfAbsent(verified.id(), verified.type(), command.payload()) == 0) return;
            if (verified.objectId() == null || !verified.objectId().startsWith("pi_")) return;
            var draft = drafts.findByStripePaymentIntentId(verified.objectId()).orElse(null);
            if (draft == null) return;
            switch (verified.type()) {
                case "payment_intent.succeeded" -> draft.confirmCapturedPayment();
                case "payment_intent.payment_failed", "payment_intent.canceled" -> draft.recordCaptureFailure();
                default -> { return; }
            }
            drafts.save(draft);
        } catch (RuntimeException exception) {
            throw new StripeWebhookProcessingFailed(exception);
        }
    }
}
