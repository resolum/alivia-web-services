package com.resolum.alivia.subscriptions.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripeAuthorization;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.BillingPeriod;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.Money;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;
    private String stripeInvoiceId;
    @Column(unique = true) private String stripePaymentIntentId;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus status;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String receiptUrl;
    private String failureMessage;
    @Column(nullable = false) private Instant createdAt;
    private Instant captureBefore;
    protected Payment() { }
    public Payment(Subscription subscription, Money amount, StripeAuthorization authorization) {
        if (subscription == null || amount == null || authorization == null)
            throw new IllegalArgumentException("La preautorización requiere contratación, importe y respuesta de Stripe.");
        this.subscription = subscription;
        this.amount = amount.amount(); this.currency = amount.currency(); this.createdAt = Instant.now();
        record(authorization);
    }
    public Payment(Subscription subscription, String stripeInvoiceId, String stripePaymentIntentId,
                   Money amount, BillingPeriod period, PaymentResult result) {
        if (subscription == null || stripeInvoiceId == null || amount == null || period == null || result == null)
            throw new IllegalArgumentException("El cobro mensual requiere contratación, factura, importe, período y resultado.");
        if (!amount.equals(subscription.getMonthlyPrice()))
            throw new IllegalArgumentException("El importe del pago no coincide con la suscripción.");
        this.subscription = subscription; this.stripeInvoiceId = stripeInvoiceId;
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.amount = amount.amount(); this.currency = amount.currency();
        this.periodStart = period.start(); this.periodEnd = period.end();
        this.status = result.status(); this.receiptUrl = result.receiptUrl(); this.failureMessage = result.failureMessage();
        this.createdAt = Instant.now();
    }
    public Long getSubscriptionId() { return subscription.getId(); }
    public Money getPaymentAmount() { return new Money(amount, currency); }
    public BillingPeriod getPeriod() { return periodStart == null ? null : new BillingPeriod(periodStart, periodEnd); }
    public void markRefunded() {
        if (status != PaymentStatus.SUCCEEDED) throw new IllegalStateException("Solo se puede reembolsar un cobro confirmado.");
        this.status = PaymentStatus.REFUNDED;
    }
    public boolean isSucceeded() { return status == PaymentStatus.SUCCEEDED; }
    public void record(StripeAuthorization authorization) {
        this.stripePaymentIntentId = authorization.intentId().value();
        this.status = authorization.state(); this.captureBefore = authorization.captureBefore();
    }
    public boolean releaseDue(Instant now) {
        var deadline = createdAt.plusSeconds(7 * 24 * 3600L);
        if (captureBefore != null && captureBefore.minusSeconds(12 * 3600L).isBefore(deadline))
            deadline = captureBefore.minusSeconds(12 * 3600L);
        return status == PaymentStatus.PREAUTHORIZED && !now.isBefore(deadline);
    }
    public void markCanceled() { this.status = PaymentStatus.CANCELED; }
}
