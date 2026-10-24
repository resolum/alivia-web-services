package com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.subscriptions.domain.model.events.StripeWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, String> {
    @Modifying
    @Query(value = """
            INSERT INTO stripe_webhook_events (id, event_type, payload, received_at)
            VALUES (:id, :eventType, :payload, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") String id, @Param("eventType") String eventType,
                       @Param("payload") String payload);
}
