package com.resolum.alivia.subscriptions.infrastructure.stripe.webhooks;

import com.resolum.alivia.subscriptions.application.outboundservices.StripeWebhookVerifier;
import com.resolum.alivia.subscriptions.domain.exceptions.InvalidStripeWebhookSignature;
import com.resolum.alivia.subscriptions.domain.exceptions.StripeWebhookUnavailable;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.VerifiedStripeWebhookEvent;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripeWebhookVerifierImpl implements StripeWebhookVerifier {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String signingSecret;

    public StripeWebhookVerifierImpl(@Value("${stripe.webhook-secret:}") String signingSecret) {
        this.signingSecret = signingSecret;
    }

    @Override
    public VerifiedStripeWebhookEvent verify(String payload, String signature) {
        if (signingSecret.isBlank()) {
            throw new StripeWebhookUnavailable();
        }
        try {
            var event = Webhook.constructEvent(payload, signature, signingSecret);
            var rawObject = JSON.readTree(event.getDataObjectDeserializer().getRawJson());
            var objectId = rawObject.hasNonNull("id") ? rawObject.get("id").asText() : null;
            return new VerifiedStripeWebhookEvent(event.getId(), event.getType(), objectId);
        } catch (SignatureVerificationException | IllegalArgumentException | JsonProcessingException exception) {
            throw new InvalidStripeWebhookSignature();
        }
    }
}
