package com.resolum.alivia.subscriptions.application.internal.commandservices;

import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnauthorized;
import com.resolum.alivia.subscriptions.domain.exceptions.PlanNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.PreauthorizationConflict;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Payment;
import com.resolum.alivia.subscriptions.domain.model.commands.CreatePreauthorizationCommand;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.*;
import com.resolum.alivia.subscriptions.domain.services.PreauthorizationCommandService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreauthorizationCommandServiceImpl implements PreauthorizationCommandService {
    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final UserStripeCustomerRepository customers;
    private final PaymentAuthorizationGateway payments;
    private final PaymentRepository paymentRecords;

    public PreauthorizationCommandServiceImpl(PlanRepository plans,
            SubscriptionRepository subscriptions, UserStripeCustomerRepository customers,
            PaymentAuthorizationGateway payments, PaymentRepository paymentRecords) {
        this.plans = plans; this.subscriptions = subscriptions;
        this.customers = customers; this.payments = payments; this.paymentRecords = paymentRecords;
    }

    @Override @Transactional
    public PreauthorizationResult handle(CreatePreauthorizationCommand command) {
        UserId userId;
        try { userId = new UserId(Long.valueOf(command.authenticatedUserId())); }
        catch (RuntimeException exception) { throw new PaymentSetupUnauthorized(); }
        var householdId = new HouseholdId(command.householdId());
        var planId = new PlanId(command.planId());
        if (command.idempotencyKey() == null || command.paymentMethodId() == null || !command.paymentMethodId().startsWith("pm_"))
            throw new IllegalArgumentException("Se requieren Idempotency-Key y PaymentMethod de Stripe.");
        var existing = subscriptions.findByIdempotencyKey(command.idempotencyKey().toString());
        if (existing.isPresent()) {
            var row = existing.get();
            if (!row.getUserId().equals(userId.value()) || !row.getHouseholdId().equals(householdId.value()) || !row.getPlan().getId().equals(planId.value()))
                throw new IllegalArgumentException("La clave de idempotencia pertenece a otra solicitud.");
            var payment = paymentRecords.findFirstBySubscription_IdAndStripeInvoiceIdIsNullOrderByIdDesc(row.getId()).orElseThrow();
            var latest = payments.retrieve(new StripePaymentIntentId(payment.getStripePaymentIntentId()));
            payment.record(latest);
            paymentRecords.save(payment);
            if (latest.state() == PaymentStatus.CANCELED || latest.state() == PaymentStatus.PAYMENT_REJECTED) { row.cancel(); subscriptions.save(row); }
            return result(row, payment, latest.clientSecret());
        }
        var plan = plans.findById(planId.value()).orElseThrow(() -> new PlanNotFound(planId.value().toString()));
        if (plan.getStatus() != PlanStatus.ACTIVE) throw new PreauthorizationConflict("El plan está inactivo.");
        if (subscriptions.existsByActiveHouseholdId(householdId.value())) throw new PreauthorizationConflict("El hogar ya tiene una contratación en curso.");
        var selection = new ActuatorSelection(command.actuators());
        var quote = plan.quoteMonthly(command.rooms(), selection);
        long cents = quote.totalMonthlyAmount().movePointRight(2).longValueExact();
        var customer = customers.findById(userId.value()).orElseThrow(() -> new IllegalArgumentException("Primero registra un medio de pago para este usuario."));
        var row = subscriptions.saveAndFlush(new Subscription(userId, householdId, plan, selection,
                new Money(quote.totalMonthlyAmount(), quote.currency()), customer.getStripeCustomerId(), command.idempotencyKey().toString()));
        var authorization = payments.create(customer.getStripeCustomerId(), command.paymentMethodId(), cents, quote.currency(), command.idempotencyKey());
        try {
            var payment = paymentRecords.saveAndFlush(new Payment(row, new Money(quote.totalMonthlyAmount(), quote.currency()), authorization));
            if (authorization.state() == PaymentStatus.CANCELED || authorization.state() == PaymentStatus.PAYMENT_REJECTED) { row.cancel(); subscriptions.save(row); }
            return result(row, payment, authorization.clientSecret());
        } catch (RuntimeException persistenceFailure) {
            try { payments.cancel(authorization.intentId()); }
            catch (RuntimeException cancellationFailure) { persistenceFailure.addSuppressed(cancellationFailure); }
            throw persistenceFailure;
        }
    }

    private PreauthorizationResult result(Subscription row, Payment payment, String clientSecret) {
        return new PreauthorizationResult(new SubscriptionId(row.getId()), new PaymentId(payment.getId()), payment.getStatus(),
                payment.getStripePaymentIntentId(), clientSecret, payment.getCaptureBefore());
    }
}
