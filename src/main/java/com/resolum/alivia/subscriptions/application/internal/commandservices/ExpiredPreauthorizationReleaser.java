package com.resolum.alivia.subscriptions.application.internal.commandservices;

import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.PaymentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class ExpiredPreauthorizationReleaser {
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository paymentRecords;
    private final PaymentAuthorizationGateway payments;
    public ExpiredPreauthorizationReleaser(SubscriptionRepository subscriptions, PaymentRepository paymentRecords, PaymentAuthorizationGateway payments) {
        this.subscriptions = subscriptions; this.paymentRecords = paymentRecords; this.payments = payments;
    }
    @Scheduled(fixedDelayString = "${payments.preauthorization.release-check-ms:3600000}")
    public void releaseExpired() {
        for (var payment : paymentRecords.findByStatus(PaymentStatus.PREAUTHORIZED)) {
            if (!payment.releaseDue(Instant.now())) continue;
            payments.cancel(new StripePaymentIntentId(payment.getStripePaymentIntentId()));
            payment.markCanceled();
            paymentRecords.save(payment);
            var subscription = subscriptions.findById(payment.getSubscriptionId()).orElseThrow();
            subscription.cancel();
            subscriptions.save(subscription);
        }
    }
}
