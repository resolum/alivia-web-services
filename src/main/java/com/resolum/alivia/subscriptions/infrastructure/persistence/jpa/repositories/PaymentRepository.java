package com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findFirstBySubscription_IdAndStripeInvoiceIdIsNullOrderByIdDesc(Long subscriptionId);
    List<Payment> findByStatus(PaymentStatus status);
}
