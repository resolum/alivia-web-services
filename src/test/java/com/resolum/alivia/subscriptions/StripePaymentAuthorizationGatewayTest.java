package com.resolum.alivia.subscriptions;

import com.resolum.alivia.subscriptions.domain.exceptions.PreauthorizationUnavailable;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import com.resolum.alivia.subscriptions.infrastructure.stripe.service.StripePaymentAuthorizationGateway;
import com.stripe.StripeClient;
import com.stripe.exception.ApiException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import com.stripe.service.PaymentIntentService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class StripePaymentAuthorizationGatewayTest {
    private final StripePaymentAuthorizationGateway gateway = new StripePaymentAuthorizationGateway("sk_test_local");

    @Test void createsManualCaptureIntentWithStableRetryKey() throws Exception {
        var service = mock(PaymentIntentService.class);
        var intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_test");
        when(intent.getStatus()).thenReturn("requires_capture");
        when(intent.getClientSecret()).thenReturn("pi_secret");
        when(service.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class))).thenReturn(intent);
        var key = UUID.randomUUID();

        try (MockedConstruction<StripeClient> ignored = mockConstruction(StripeClient.class,
                (client, context) -> when(client.paymentIntents()).thenReturn(service))) {
            var result = gateway.create("cus_test", "pm_test", 9900L, "PEN", key);
            assertEquals(PaymentStatus.PREAUTHORIZED, result.state());
            assertEquals("pi_secret", result.clientSecret());
        }
        verify(service).create(argThat(params -> params.getAmount() == 9900L
                        && "pen".equals(params.getCurrency())
                        && "cus_test".equals(params.getCustomer())
                        && "pm_test".equals(params.getPaymentMethod())
                        && params.getCaptureMethod() == PaymentIntentCreateParams.CaptureMethod.MANUAL
                        && Boolean.TRUE.equals(params.getConfirm())
                        && Boolean.TRUE.equals(params.getOffSession())),
                argThat(options -> ("preauthorization-" + key).equals(options.getIdempotencyKey())));
    }

    @Test void retrievesProviderStatusAndCancelsAuthorization() throws Exception {
        var service = mock(PaymentIntentService.class);
        var intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_test");
        when(intent.getStatus()).thenReturn("canceled");
        when(service.retrieve(eq("pi_test"), any(PaymentIntentRetrieveParams.class))).thenReturn(intent);
        try (MockedConstruction<StripeClient> ignored = mockConstruction(StripeClient.class,
                (client, context) -> when(client.paymentIntents()).thenReturn(service))) {
            assertEquals(PaymentStatus.CANCELED, gateway.retrieve(new StripePaymentIntentId("pi_test")).state());
            gateway.cancel(new StripePaymentIntentId("pi_test"));
        }
        verify(service).cancel("pi_test");
    }

    @Test void missingConfigurationAndProviderOutageAreReported() throws Exception {
        var unconfigured = new StripePaymentAuthorizationGateway("");
        var id = new StripePaymentIntentId("pi_test");
        assertThrows(PreauthorizationUnavailable.class, () -> unconfigured.create("cus", "pm", 100L, "PEN", UUID.randomUUID()));
        assertThrows(PreauthorizationUnavailable.class, () -> unconfigured.retrieve(id));
        assertThrows(PreauthorizationUnavailable.class, () -> unconfigured.cancel(id));

        var service = mock(PaymentIntentService.class);
        var outage = new ApiException("provider detail", null, null, 503, null);
        when(service.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class))).thenThrow(outage);
        when(service.retrieve(eq("pi_test"), any(PaymentIntentRetrieveParams.class))).thenThrow(outage);
        when(service.cancel("pi_test")).thenThrow(outage);
        try (MockedConstruction<StripeClient> ignored = mockConstruction(StripeClient.class,
                (client, context) -> when(client.paymentIntents()).thenReturn(service))) {
            assertThrows(PreauthorizationUnavailable.class,
                    () -> gateway.create("cus", "pm", 100L, "PEN", UUID.randomUUID()));
            assertThrows(PreauthorizationUnavailable.class, () -> gateway.retrieve(id));
            assertThrows(PreauthorizationUnavailable.class, () -> gateway.cancel(id));
        }
    }
}
