package com.resolum.alivia.subscriptions;

import com.resolum.alivia.subscriptions.application.internal.commandservices.PreauthorizationCommandServiceImpl;
import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.exceptions.PreauthorizationConflict;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Payment;
import com.resolum.alivia.subscriptions.domain.model.commands.CreatePreauthorizationCommand;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.*;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreauthorizationTest {
    private final PlanRepository plans = mock(PlanRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final UserStripeCustomerRepository customers = mock(UserStripeCustomerRepository.class);
    private final PaymentAuthorizationGateway payments = mock(PaymentAuthorizationGateway.class);
    private final PaymentRepository paymentRecords = mock(PaymentRepository.class);
    private final PreauthorizationCommandServiceImpl service = new PreauthorizationCommandServiceImpl(
            plans, subscriptions, customers, payments, paymentRecords);

    private CreatePreauthorizationCommand command() {
        return new CreatePreauthorizationCommand("1", "home-1", 1L, 1,
                List.of(new ActuatorQuantity(ActuatorType.LIGHT, 1)), "pm_test", UUID.randomUUID());
    }

    @Test void requiresAuthenticatedUserBeforeStripe() {
        var request = command();
        assertThrows(RuntimeException.class, () -> service.handle(new CreatePreauthorizationCommand(null,
                request.householdId(), request.planId(), request.rooms(), request.actuators(), request.paymentMethodId(), request.idempotencyKey())));
        verifyNoInteractions(payments);
    }

    @Test void rejectsDuplicateHouseholdBeforeStripe() {
        var request = command();
        var plan = mock(com.resolum.alivia.subscriptions.domain.model.aggregates.Plan.class);
        when(plans.findById(1L)).thenReturn(java.util.Optional.of(plan));
        when(plan.getStatus()).thenReturn(PlanStatus.ACTIVE);
        when(subscriptions.existsByActiveHouseholdId("home-1")).thenReturn(true);
        assertThrows(PreauthorizationConflict.class, () -> service.handle(request));
        verifyNoInteractions(payments);
    }

    @Test void householdIdIsValidatedAsValueObject() {
        assertThrows(IllegalArgumentException.class, () -> new HouseholdId(" "));
        assertEquals("home-1", new HouseholdId(" home-1 ").value());
    }

    @Test void paymentReleasesBeforeProviderCaptureDeadlineAndSubscriptionStaysPending() {
        var subscription = new com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription(
                new UserId(1L), new HouseholdId("home-1"), mock(com.resolum.alivia.subscriptions.domain.model.aggregates.Plan.class),
                new ActuatorSelection(List.of(new ActuatorQuantity(ActuatorType.LIGHT, 1))),
                new Money(new java.math.BigDecimal("10.00"), "PEN"), "cus_test", UUID.randomUUID().toString());
        var expiry = Instant.now().plusSeconds(24 * 3600);
        var payment = new Payment(subscription, new Money(new java.math.BigDecimal("10.00"), "PEN"),
                new StripeAuthorization(new StripePaymentIntentId("pi_test"), null, PaymentStatus.PREAUTHORIZED, expiry));
        assertFalse(payment.releaseDue(Instant.now()));
        assertTrue(payment.releaseDue(expiry.minusSeconds(11 * 3600)));
        assertEquals(SubscriptionStatus.PENDING_PAYMENT, subscription.getStatus());
        payment.markCanceled(); subscription.cancel();
        assertEquals(PaymentStatus.CANCELED, payment.getStatus());
        assertNull(subscription.getActiveHouseholdId());
    }

    @Test void subscriptionKeepsQuotedPlanAndOptionalContextSeparateFromPayment() {
        var plan = mock(com.resolum.alivia.subscriptions.domain.model.aggregates.Plan.class);
        var subscription = new com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription(
                new UserId(7L), new HouseholdId("home-7"), plan,
                new ActuatorSelection(List.of(new ActuatorQuantity(ActuatorType.DOOR, 2))),
                new Money(new java.math.BigDecimal("89.90"), "PEN"), "cus_7", "request-7");
        subscription.linkContext(new AssistedPersonId(3L), new BusinessId(4L));
        var payment = new Payment(subscription, new Money(new java.math.BigDecimal("89.90"), "PEN"),
                new StripeAuthorization(new StripePaymentIntentId("pi_7"), "secret", PaymentStatus.PREAUTHORIZED,
                        Instant.now().plusSeconds(86400)));
        assertSame(plan, subscription.getPlan());
        assertEquals(2, subscription.getActuatorSelection().quantityOf(ActuatorType.DOOR));
        assertEquals(new java.math.BigDecimal("89.90"), subscription.getMonthlyPrice().amount());
        assertEquals(new AssistedPersonId(3L), subscription.getAssistedPerson());
        assertEquals(new BusinessId(4L), subscription.getBusiness());
        assertSame(subscription, payment.getSubscription());
        assertEquals(subscription.getMonthlyPrice(), payment.getPaymentAmount());
        assertNull(payment.getStripeInvoiceId());
        assertNull(payment.getPeriod());
    }
}
