package com.resolum.alivia.subscriptions;

import com.resolum.alivia.subscriptions.application.internal.commandservices.ExpiredPreauthorizationReleaser;
import com.resolum.alivia.subscriptions.application.internal.commandservices.PreauthorizationCommandServiceImpl;
import com.resolum.alivia.subscriptions.application.internal.queryservices.PreauthorizationQueryServiceImpl;
import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnauthorized;
import com.resolum.alivia.subscriptions.domain.exceptions.PlanNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.PreauthorizationConflict;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Payment;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.commands.CreatePreauthorizationCommand;
import com.resolum.alivia.subscriptions.domain.model.entities.UserStripeCustomer;
import com.resolum.alivia.subscriptions.domain.model.factories.PlanCatalogDefaults;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.*;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PreauthorizationFlowTest {
    private final PlanRepository plans = mock(PlanRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final UserStripeCustomerRepository customers = mock(UserStripeCustomerRepository.class);
    private final PaymentRepository records = mock(PaymentRepository.class);
    private final PaymentAuthorizationGateway stripe = mock(PaymentAuthorizationGateway.class);

    private Subscription subscription() {
        var plan = PlanCatalogDefaults.base();
        ReflectionTestUtils.setField(plan, "id", 1L);
        var row = new Subscription(new UserId(7L), new HouseholdId("home-7"), plan,
                new ActuatorSelection(List.of(new ActuatorQuantity(ActuatorType.LIGHT, 1))),
                new Money(new BigDecimal("99.00"), "PEN"), "cus_7", "request-7");
        ReflectionTestUtils.setField(row, "id", 41L);
        return row;
    }

    private Payment payment(Subscription row, PaymentStatus status) {
        var payment = new Payment(row, row.getMonthlyPrice(),
                new StripeAuthorization(new StripePaymentIntentId("pi_7"), "secret", status,
                        Instant.now().plusSeconds(86400)));
        ReflectionTestUtils.setField(payment, "id", 52L);
        return payment;
    }

    private CreatePreauthorizationCommand command(UUID key) {
        return new CreatePreauthorizationCommand("7", "home-7", 1L, 1,
                List.of(new ActuatorQuantity(ActuatorType.LIGHT, 1)), "pm_7", key);
    }

    @Test void createsPendingContractAndManualAuthorizationUsingServerQuote() {
        var row = subscription();
        UUID key = UUID.randomUUID();
        when(plans.findById(1L)).thenReturn(Optional.of(row.getPlan()));
        when(customers.findById(7L)).thenReturn(Optional.of(new UserStripeCustomer(new UserId(7L), "cus_7")));
        when(subscriptions.saveAndFlush(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 41L);
            return saved;
        });
        when(stripe.create("cus_7", "pm_7", 9900L, "PEN", key)).thenReturn(
                new StripeAuthorization(new StripePaymentIntentId("pi_7"), "secret", PaymentStatus.PREAUTHORIZED,
                        Instant.now().plusSeconds(86400)));
        when(records.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 52L);
            return saved;
        });

        var result = new PreauthorizationCommandServiceImpl(plans, subscriptions, customers, stripe, records).handle(command(key));

        assertEquals(41L, result.subscriptionId().value());
        assertEquals(52L, result.paymentId().value());
        assertEquals(PaymentStatus.PREAUTHORIZED, result.status());
        assertEquals("secret", result.clientSecret());
        verify(stripe).create("cus_7", "pm_7", 9900L, "PEN", key);
    }

    @Test void rejectedProviderStateCancelsPendingContract() {
        var row = subscription();
        UUID key = UUID.randomUUID();
        when(plans.findById(1L)).thenReturn(Optional.of(row.getPlan()));
        when(customers.findById(7L)).thenReturn(Optional.of(new UserStripeCustomer(new UserId(7L), "cus_7")));
        when(subscriptions.saveAndFlush(any(Subscription.class))).thenReturn(row);
        when(stripe.create("cus_7", "pm_7", 9900L, "PEN", key)).thenReturn(
                new StripeAuthorization(new StripePaymentIntentId("pi_7"), null, PaymentStatus.PAYMENT_REJECTED, null));
        when(records.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 52L);
            return saved;
        });

        var result = new PreauthorizationCommandServiceImpl(plans, subscriptions, customers, stripe, records).handle(command(key));

        assertEquals(PaymentStatus.PAYMENT_REJECTED, result.status());
        assertEquals(SubscriptionStatus.CANCELED, row.getStatus());
        verify(subscriptions).save(row);
    }

    @Test void failedLocalPaymentSaveReleasesProviderAuthorization() {
        var row = subscription();
        UUID key = UUID.randomUUID();
        when(plans.findById(1L)).thenReturn(Optional.of(row.getPlan()));
        when(customers.findById(7L)).thenReturn(Optional.of(new UserStripeCustomer(new UserId(7L), "cus_7")));
        when(subscriptions.saveAndFlush(any(Subscription.class))).thenReturn(row);
        when(stripe.create("cus_7", "pm_7", 9900L, "PEN", key)).thenReturn(
                new StripeAuthorization(new StripePaymentIntentId("pi_7"), null, PaymentStatus.PREAUTHORIZED, null));
        var storageFailure = new IllegalStateException("storage unavailable");
        when(records.saveAndFlush(any(Payment.class))).thenThrow(storageFailure);

        assertSame(storageFailure, assertThrows(IllegalStateException.class,
                () -> new PreauthorizationCommandServiceImpl(plans, subscriptions, customers, stripe, records).handle(command(key))));
        verify(stripe).cancel(new StripePaymentIntentId("pi_7"));
    }

    @Test void retryReadsSamePaymentAndRefreshesProviderState() {
        var row = subscription();
        var payment = payment(row, PaymentStatus.AWAITING_CONFIRMATION);
        UUID key = UUID.randomUUID();
        ReflectionTestUtils.setField(row, "idempotencyKey", key.toString());
        when(subscriptions.findByIdempotencyKey(key.toString())).thenReturn(Optional.of(row));
        when(records.findFirstBySubscription_IdAndStripeInvoiceIdIsNullOrderByIdDesc(41L)).thenReturn(Optional.of(payment));
        when(stripe.retrieve(new StripePaymentIntentId("pi_7"))).thenReturn(
                new StripeAuthorization(new StripePaymentIntentId("pi_7"), "secret", PaymentStatus.PREAUTHORIZED, null));

        var result = new PreauthorizationCommandServiceImpl(plans, subscriptions, customers, stripe, records).handle(command(key));

        assertEquals(PaymentStatus.PREAUTHORIZED, result.status());
        verify(stripe, never()).create(any(), any(), anyLong(), any(), any());
        verify(records).save(payment);
    }

    @Test void mismatchedRetryCannotReuseAnotherContract() {
        var row = subscription();
        UUID key = UUID.randomUUID();
        when(subscriptions.findByIdempotencyKey(key.toString())).thenReturn(Optional.of(row));
        var otherHouse = new CreatePreauthorizationCommand("7", "home-8", 1L, 1,
                List.of(new ActuatorQuantity(ActuatorType.LIGHT, 1)), "pm_7", key);
        assertThrows(IllegalArgumentException.class,
                () -> new PreauthorizationCommandServiceImpl(plans, subscriptions, customers, stripe, records).handle(otherHouse));
        verifyNoInteractions(stripe);
    }

    @Test void inactivePlanStopsBeforeContactingProvider() {
        var plan = PlanCatalogDefaults.base();
        ReflectionTestUtils.setField(plan, "id", 1L);
        ReflectionTestUtils.setField(plan, "status", PlanStatus.INACTIVE);
        when(plans.findById(1L)).thenReturn(Optional.of(plan));
        assertThrows(PreauthorizationConflict.class,
                () -> new PreauthorizationCommandServiceImpl(plans, subscriptions, customers, stripe, records)
                        .handle(command(UUID.randomUUID())));
        verifyNoInteractions(stripe);
    }

    @Test void missingPlanAndMissingCustomerNeverRequestCardHold() {
        var service = new PreauthorizationCommandServiceImpl(plans, subscriptions, customers, stripe, records);
        assertThrows(PlanNotFound.class, () -> service.handle(command(UUID.randomUUID())));
        var plan = PlanCatalogDefaults.base();
        ReflectionTestUtils.setField(plan, "id", 1L);
        when(plans.findById(1L)).thenReturn(Optional.of(plan));
        assertThrows(IllegalArgumentException.class, () -> service.handle(command(UUID.randomUUID())));
        verifyNoInteractions(stripe);
        verify(subscriptions, never()).saveAndFlush(any());
    }

    @Test void invalidPaymentReferenceAndMissingIdempotencyKeyStopBeforeProvider() {
        var service = new PreauthorizationCommandServiceImpl(plans, subscriptions, customers, stripe, records);
        var invalidMethod = new CreatePreauthorizationCommand("7", "home-7", 1L, 1,
                List.of(new ActuatorQuantity(ActuatorType.LIGHT, 1)), "card_number", UUID.randomUUID());
        var missingKey = new CreatePreauthorizationCommand("7", "home-7", 1L, 1,
                List.of(new ActuatorQuantity(ActuatorType.LIGHT, 1)), "pm_7", null);
        assertThrows(IllegalArgumentException.class, () -> service.handle(invalidMethod));
        assertThrows(IllegalArgumentException.class, () -> service.handle(missingKey));
        verifyNoInteractions(stripe);
    }

    @Test void queryRejectsOtherUserAndReconcilesCancellation() {
        var row = subscription();
        var payment = payment(row, PaymentStatus.PREAUTHORIZED);
        when(subscriptions.findById(41L)).thenReturn(Optional.of(row));
        var query = new PreauthorizationQueryServiceImpl(subscriptions, records, stripe);
        assertThrows(PaymentSetupUnauthorized.class, () -> query.get("8", 41L));
        verifyNoInteractions(stripe);

        when(records.findFirstBySubscription_IdAndStripeInvoiceIdIsNullOrderByIdDesc(41L)).thenReturn(Optional.of(payment));
        when(stripe.retrieve(new StripePaymentIntentId("pi_7"))).thenReturn(
                new StripeAuthorization(new StripePaymentIntentId("pi_7"), null, PaymentStatus.CANCELED, null));
        var result = query.get("7", 41L);
        assertEquals(PaymentStatus.CANCELED, result.status());
        assertNull(result.clientSecret());
        assertEquals(SubscriptionStatus.CANCELED, row.getStatus());
        verify(subscriptions).save(row);
    }

    @Test void expiryReleasesOnlyDueAuthorizations() {
        var row = subscription();
        var due = payment(row, PaymentStatus.PREAUTHORIZED);
        var notDue = payment(row, PaymentStatus.AWAITING_CONFIRMATION);
        ReflectionTestUtils.setField(due, "createdAt", Instant.now().minusSeconds(8 * 86400));
        when(records.findByStatus(PaymentStatus.PREAUTHORIZED)).thenReturn(List.of(due, notDue));
        when(subscriptions.findById(41L)).thenReturn(Optional.of(row));

        new ExpiredPreauthorizationReleaser(subscriptions, records, stripe).releaseExpired();

        verify(stripe, times(1)).cancel(new StripePaymentIntentId("pi_7"));
        assertEquals(PaymentStatus.CANCELED, due.getStatus());
        assertEquals(PaymentStatus.AWAITING_CONFIRMATION, notDue.getStatus());
        assertEquals(SubscriptionStatus.CANCELED, row.getStatus());
    }

    @Test void subscriptionActivatesOnlyAfterStripeLinkAndValidBillingPeriod() {
        var row = subscription();
        var start = LocalDateTime.now().minusDays(1);
        var period = new BillingPeriod(start, start.plusMonths(1));
        assertNull(row.getCurrentPeriod());
        assertFalse(row.isCurrent());
        assertThrows(IllegalStateException.class, () -> row.activate(period));
        row.linkStripeSubscription("sub_test");
        row.activate(period);
        assertEquals(period, row.getCurrentPeriod());
        assertTrue(row.isActive());
        assertTrue(row.isCurrent());
        row.markPastDue();
        assertFalse(row.isCurrent());
        row.refund();
        assertTrue(row.isRefunded());
    }

    @Test void monthlyPaymentRequiresMatchingAmountAndOnlyCapturedPaymentCanBeRefunded() {
        var row = subscription();
        var start = LocalDateTime.now();
        var period = new BillingPeriod(start, start.plusMonths(1));
        assertThrows(IllegalArgumentException.class, () -> new Payment(row, "in_test", "pi_test",
                new Money(new BigDecimal("98.00"), "PEN"), period, PaymentResult.succeeded(null)));
        var failed = new Payment(row, "in_test", "pi_test", row.getMonthlyPrice(), period,
                PaymentResult.failed("card declined"));
        assertEquals(period, failed.getPeriod());
        assertEquals("card declined", failed.getFailureMessage());
        assertThrows(IllegalStateException.class, failed::markRefunded);
        var succeeded = new Payment(row, "in_2", "pi_2", row.getMonthlyPrice(), period,
                PaymentResult.succeeded("receipt"));
        assertTrue(succeeded.isSucceeded());
        assertEquals("receipt", succeeded.getReceiptUrl());
        succeeded.markRefunded();
        assertEquals(PaymentStatus.REFUNDED, succeeded.getStatus());
        assertFalse(succeeded.isSucceeded());
    }
}
