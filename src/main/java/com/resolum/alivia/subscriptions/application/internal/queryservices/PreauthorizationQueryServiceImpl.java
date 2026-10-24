package com.resolum.alivia.subscriptions.application.internal.queryservices;

import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnauthorized;
import com.resolum.alivia.subscriptions.domain.exceptions.PlanNotFound;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.*;
import com.resolum.alivia.subscriptions.domain.services.PreauthorizationQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreauthorizationQueryServiceImpl implements PreauthorizationQueryService {
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository paymentRecords;
    private final PaymentAuthorizationGateway payments;
    public PreauthorizationQueryServiceImpl(SubscriptionRepository subscriptions,
                                           PaymentRepository paymentRecords, PaymentAuthorizationGateway payments) {
        this.subscriptions = subscriptions; this.paymentRecords = paymentRecords; this.payments = payments;
    }
    @Override @Transactional
    public PreauthorizationResult get(String authenticatedUserId, Long subscriptionId) {
        if (authenticatedUserId == null) throw new PaymentSetupUnauthorized();
        var row = subscriptions.findById(subscriptionId).orElseThrow(() -> new PlanNotFound(subscriptionId.toString()));
        if (!row.getUserId().toString().equals(authenticatedUserId)) throw new PaymentSetupUnauthorized();
        var payment = paymentRecords.findFirstBySubscription_IdAndStripeInvoiceIdIsNullOrderByIdDesc(subscriptionId).orElseThrow();
        var latest = payments.retrieve(new StripePaymentIntentId(payment.getStripePaymentIntentId()));
        payment.record(latest);
        paymentRecords.save(payment);
        if (latest.state() == PaymentStatus.CANCELED || latest.state() == PaymentStatus.PAYMENT_REJECTED) { row.cancel(); subscriptions.save(row); }
        return new PreauthorizationResult(new SubscriptionId(row.getId()), new PaymentId(payment.getId()), payment.getStatus(),
                payment.getStripePaymentIntentId(), null, payment.getCaptureBefore());
    }
}
