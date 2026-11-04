package com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigurationDraftRepository extends JpaRepository<ConfigurationDraft, String> {
    List<ConfigurationDraft> findByPaymentStatus(PaymentStatus status);

    /** Requests of the family's account, latest first. */
    List<ConfigurationDraft> findByOwnerUserIdOrderByUpdatedAtDesc(Long ownerUserId);

    Optional<ConfigurationDraft> findByStripePaymentIntentId(String stripePaymentIntentId);
}
