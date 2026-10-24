package com.resolum.alivia.subscriptions.domain.model.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
public class StripeWebhookEvent {
    @Id
    @Column(length = 255)
    private String id;

    @Column(nullable = false, length = 255)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private Instant receivedAt;

    protected StripeWebhookEvent() {
    }

    public StripeWebhookEvent(String id, String eventType, String payload, Instant receivedAt) {
        if (id == null || id.isBlank() || eventType == null || eventType.isBlank()
                || payload == null || payload.isBlank() || receivedAt == null) {
            throw new IllegalArgumentException("El evento verificado de Stripe está incompleto.");
        }
        this.id = id;
        this.eventType = eventType;
        this.payload = payload;
        this.receivedAt = receivedAt;
    }
}
