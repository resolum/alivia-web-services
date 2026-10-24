package com.resolum.alivia.subscriptions;

import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnavailable;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.UserId;
import com.resolum.alivia.subscriptions.infrastructure.stripe.service.StripeServiceImpl;
import com.stripe.StripeClient;
import com.stripe.exception.ApiException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.Customer;
import com.stripe.model.SetupIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.service.CustomerService;
import com.stripe.service.SetupIntentService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripeServiceImplTest {
    private final StripeServiceImpl service = new StripeServiceImpl("sk_test_local", "pk_test_local");

    @Test
    void createsCustomerWithUserMetadataAndStableIdempotencyKey() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn("cus_test");
        when(customers.create(org.mockito.ArgumentMatchers.any(CustomerCreateParams.class),
                org.mockito.ArgumentMatchers.any(RequestOptions.class))).thenReturn(customer);

        try (MockedConstruction<StripeClient> clients = mockConstruction(StripeClient.class,
                (client, context) -> when(client.customers()).thenReturn(customers))) {
            assertEquals("cus_test", service.createCustomer(new UserId(42L)));
            assertEquals(1, clients.constructed().size());
        }
        verify(customers).create(argThat(params -> "42".equals(((Map<?, ?>) params.getMetadata()).get("alivia_user_id"))),
                argThat(options -> "alivia-user-customer-42".equals(options.getIdempotencyKey())));
    }

    @Test
    void createsOffSessionCardSetupForCustomerWithStableRetryKey() throws Exception {
        SetupIntentService setups = mock(SetupIntentService.class);
        SetupIntent intent = mock(SetupIntent.class);
        when(intent.getId()).thenReturn("seti_test");
        when(intent.getClientSecret()).thenReturn("seti_test_secret");
        when(setups.create(org.mockito.ArgumentMatchers.any(SetupIntentCreateParams.class),
                org.mockito.ArgumentMatchers.any(RequestOptions.class))).thenReturn(intent);
        UUID key = UUID.randomUUID();

        try (MockedConstruction<StripeClient> ignored = mockConstruction(StripeClient.class,
                (client, context) -> when(client.setupIntents()).thenReturn(setups))) {
            var result = service.createPaymentSetup("cus_test", key);
            assertEquals("seti_test", result.setupIntentId());
            assertEquals("seti_test_secret", result.clientSecret());
            assertEquals("pk_test_local", result.publishableKey());
        }
        verify(setups).create(argThat(params -> "cus_test".equals(params.getCustomer())
                        && params.getUsage() == SetupIntentCreateParams.Usage.OFF_SESSION
                        && params.getPaymentMethodTypes().contains("card")),
                argThat(options -> ("payment-setup-cus_test-" + key).equals(options.getIdempotencyKey())));
    }

    @Test
    void retrievesSetupStateFromStripe() throws Exception {
        SetupIntentService setups = mock(SetupIntentService.class);
        SetupIntent intent = mock(SetupIntent.class);
        when(intent.getId()).thenReturn("seti_test");
        when(intent.getCustomer()).thenReturn("cus_test");
        when(intent.getStatus()).thenReturn("succeeded");
        when(intent.getPaymentMethod()).thenReturn("pm_test");
        when(setups.retrieve("seti_test")).thenReturn(intent);

        try (MockedConstruction<StripeClient> ignored = mockConstruction(StripeClient.class,
                (client, context) -> when(client.setupIntents()).thenReturn(setups))) {
            var result = service.retrievePaymentSetup("seti_test");
            assertEquals("cus_test", result.customerId());
            assertEquals("succeeded", result.status());
            assertEquals("pm_test", result.paymentMethodId());
        }
    }

    @Test
    void mapsStripeFailuresWithoutExposingProviderDetails() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        SetupIntentService setups = mock(SetupIntentService.class);
        ApiException outage = new ApiException("provider detail", null, null, 503, null);
        when(customers.create(org.mockito.ArgumentMatchers.any(CustomerCreateParams.class),
                org.mockito.ArgumentMatchers.any(RequestOptions.class))).thenThrow(outage);
        when(setups.create(org.mockito.ArgumentMatchers.any(SetupIntentCreateParams.class),
                org.mockito.ArgumentMatchers.any(RequestOptions.class))).thenThrow(outage);
        when(setups.retrieve("seti_missing"))
                .thenThrow(new InvalidRequestException("missing", null, null, null, 404, null));
        when(setups.retrieve("seti_outage")).thenThrow(outage);

        try (MockedConstruction<StripeClient> ignored = mockConstruction(StripeClient.class,
                (client, context) -> {
                    when(client.customers()).thenReturn(customers);
                    when(client.setupIntents()).thenReturn(setups);
                })) {
            assertThrows(PaymentSetupUnavailable.class, () -> service.createCustomer(new UserId(42L)));
            assertThrows(PaymentSetupUnavailable.class,
                    () -> service.createPaymentSetup("cus_test", UUID.randomUUID()));
            assertThrows(PaymentSetupNotFound.class, () -> service.retrievePaymentSetup("seti_missing"));
            assertThrows(PaymentSetupUnavailable.class, () -> service.retrievePaymentSetup("seti_outage"));
        }
    }

    @Test
    void refusesMissingConfigurationOrCustomerBeforeCallingStripe() {
        assertThrows(PaymentSetupUnavailable.class,
                () -> new StripeServiceImpl("", "pk_test_local").createCustomer(new UserId(42L)));
        assertThrows(PaymentSetupUnavailable.class,
                () -> new StripeServiceImpl("sk_test_local", "").retrievePaymentSetup("seti_test"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createPaymentSetup(" ", UUID.randomUUID()));
    }
}
