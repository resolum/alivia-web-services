package com.resolum.alivia.subscriptions;

import com.resolum.alivia.subscriptions.application.internal.commandservices.PaymentSetupCommandServiceImpl;
import com.resolum.alivia.subscriptions.application.internal.queryservices.PaymentSetupQueryServiceImpl;
import com.resolum.alivia.subscriptions.application.outboundservices.StripeService;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnauthorized;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnavailable;
import com.resolum.alivia.subscriptions.domain.model.commands.PreparePaymentSetupCommand;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPaymentSetupStatusQuery;
import com.resolum.alivia.subscriptions.domain.model.entities.UserStripeCustomer;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetup;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetupStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.UserId;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.UserStripeCustomerRepository;
import com.resolum.alivia.subscriptions.infrastructure.stripe.service.StripeServiceImpl;
import com.resolum.alivia.subscriptions.interfaces.rest.controllers.PaymentsController;
import com.resolum.alivia.subscriptions.domain.services.PaymentSetupQueryService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentSetupTest {
    @Test
    void controllerUsesUserIdAndExistingStripeCustomer() {
        StripeService gateway = mock(StripeService.class);
        UserStripeCustomerRepository customers = mock(UserStripeCustomerRepository.class);
        UUID key = UUID.randomUUID();
        when(customers.findById(42L)).thenReturn(Optional.of(new UserStripeCustomer(new UserId(42L), "cus_test")));
        when(gateway.createPaymentSetup("cus_test", key)).thenReturn(new PaymentSetup("seti_test", "secret", "pk_test"));

        var response = new PaymentsController(new PaymentSetupCommandServiceImpl(gateway, customers),
                mock(com.resolum.alivia.subscriptions.domain.services.PaymentSetupQueryService.class))
                .prepareSetup(() -> "42", key);

        assertEquals("seti_test", response.setupIntentId());
        verify(gateway).createPaymentSetup("cus_test", key);
    }

    @Test
    void createsCustomerMappingForUser() {
        StripeService gateway = mock(StripeService.class);
        UserStripeCustomerRepository customers = mock(UserStripeCustomerRepository.class);
        UUID key = UUID.randomUUID();
        when(customers.findById(42L)).thenReturn(Optional.empty());
        when(gateway.createCustomer(new UserId(42L))).thenReturn("cus_test");
        when(gateway.createPaymentSetup("cus_test", key)).thenReturn(new PaymentSetup("seti_test", "secret", "pk_test"));

        new PaymentSetupCommandServiceImpl(gateway, customers)
                .handle(new PreparePaymentSetupCommand("42", key));

        verify(customers).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                mapping -> mapping.getUserId().equals(42L) && mapping.getStripeCustomerId().equals("cus_test")));
    }

    @Test
    void rejectsMissingIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new UserId(null));
        assertThrows(IllegalArgumentException.class, () -> new PreparePaymentSetupCommand("42", null));
    }

    @Test
    void paymentSetupRequiresAuthenticatedNumericUserId() {
        var service = new PaymentSetupCommandServiceImpl(mock(StripeService.class),
                mock(UserStripeCustomerRepository.class));
        assertThrows(PaymentSetupUnauthorized.class,
                () -> service.handle(new PreparePaymentSetupCommand(null, UUID.randomUUID())));
        assertThrows(PaymentSetupUnauthorized.class,
                () -> service.handle(new PreparePaymentSetupCommand("someone@example.com", UUID.randomUUID())));
    }

    @Test
    void unavailableKeysDoNotCallStripe() {
        var gateway = new StripeServiceImpl("", "");
        assertThrows(PaymentSetupUnavailable.class, () -> gateway.createPaymentSetup("cus_test", UUID.randomUUID()));
    }

    @Test
    void setupStatusIsVisibleOnlyToItsCustomer() {
        StripeService stripe = mock(StripeService.class);
        UserStripeCustomerRepository customers = mock(UserStripeCustomerRepository.class);
        when(customers.findById(42L)).thenReturn(Optional.of(new UserStripeCustomer(new UserId(42L), "cus_own")));
        var service = new PaymentSetupQueryServiceImpl(stripe, customers);
        when(stripe.retrievePaymentSetup("seti_own"))
                .thenReturn(new PaymentSetupStatus("seti_own", "cus_own", "succeeded", "pm_test"));
        when(stripe.retrievePaymentSetup("seti_other"))
                .thenReturn(new PaymentSetupStatus("seti_other", "cus_other", "succeeded", "pm_other"));

        assertEquals("succeeded", service.handle(new GetPaymentSetupStatusQuery("42", "seti_own")).status());
        assertThrows(PaymentSetupNotFound.class,
                () -> service.handle(new GetPaymentSetupStatusQuery("42", "seti_other")));
        assertThrows(PaymentSetupUnauthorized.class,
                () -> service.handle(new GetPaymentSetupStatusQuery(null, "seti_own")));
    }

    @Test
    void statusEndpointReportsRegisteredCardWithoutExposingSecret() {
        PaymentSetupQueryService queries = mock(PaymentSetupQueryService.class);
        when(queries.handle(new GetPaymentSetupStatusQuery("42", "seti_test")))
                .thenReturn(new PaymentSetupStatus("seti_test", "cus_test", "succeeded", "pm_test"));
        var controller = new PaymentsController(mock(com.resolum.alivia.subscriptions.domain.services.PaymentSetupCommandService.class),
                queries);

        var response = controller.getSetupStatus(() -> "42", "seti_test");

        assertEquals("seti_test", response.setupIntentId());
        assertEquals("succeeded", response.status());
        assertEquals(true, response.paymentMethodRegistered());

        when(queries.handle(new GetPaymentSetupStatusQuery("42", "seti_pending")))
                .thenReturn(new PaymentSetupStatus("seti_pending", "cus_test", "requires_payment_method", null));
        assertEquals(false, controller.getSetupStatus(() -> "42", "seti_pending").paymentMethodRegistered());
    }
}
