package com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConfigurationDraftRepository extends JpaRepository<ConfigurationDraft, String> {
    List<ConfigurationDraft> findByPaymentStatus(PaymentStatus status);

    /** Drafts in one of the states whose payment was authorized in the period ({@code from} included). */
    List<ConfigurationDraft> findByPaymentStatusInAndPreauthorizedAtGreaterThanEqualAndPreauthorizedAtLessThan(
            Collection<PaymentStatus> statuses, Instant from, Instant to);

    /** Requests of the family's account, latest first. */
    List<ConfigurationDraft> findByOwnerUserIdOrderByUpdatedAtDesc(Long ownerUserId);

    Optional<ConfigurationDraft> findByStripePaymentIntentId(String stripePaymentIntentId);
}
