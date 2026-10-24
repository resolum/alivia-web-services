package com.resolum.alivia.subscriptions;

import com.resolum.alivia.subscriptions.application.internal.eventhandlers.StripeWebhookEventHandlerImpl;
import com.resolum.alivia.subscriptions.domain.exceptions.InvalidStripeWebhookSignature;
import com.resolum.alivia.subscriptions.domain.exceptions.StripeWebhookUnavailable;
import com.resolum.alivia.subscriptions.domain.model.commands.ReceiveStripeWebhookCommand;
import com.resolum.alivia.subscriptions.domain.model.events.StripeWebhookEvent;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.StripeWebhookEventRepository;
import com.resolum.alivia.subscriptions.infrastructure.stripe.webhooks.StripeWebhookVerifierImpl;
import com.resolum.alivia.subscriptions.interfaces.webhooks.StripeWebhookController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StripeWebhookTest {
    private static final String SECRET = "whsec_local_test";
    private static final String PAYLOAD = """
            {"id":"evt_test","object":"event","type":"setup_intent.succeeded","data":{"object":{"id":"seti_test","object":"setup_intent"}}}
            """.strip();

    @Test
    void verifiesUnmodifiedPayloadAndStoresEachEventOnce() throws Exception {
        var events = mock(StripeWebhookEventRepository.class);
        var service = new StripeWebhookEventHandlerImpl(new StripeWebhookVerifierImpl(SECRET), events);
        var command = new ReceiveStripeWebhookCommand(PAYLOAD, signedHeader(PAYLOAD));
        when(events.insertIfAbsent("evt_test", "setup_intent.succeeded", PAYLOAD)).thenReturn(1, 0);

        service.handle(command);
        service.handle(command);

        verify(events, org.mockito.Mockito.times(2))
                .insertIfAbsent("evt_test", "setup_intent.succeeded", PAYLOAD);
    }

    @Test
    void rejectsTamperedPayloadWithoutWritingAnEvent() throws Exception {
        var events = mock(StripeWebhookEventRepository.class);
        var service = new StripeWebhookEventHandlerImpl(new StripeWebhookVerifierImpl(SECRET), events);
        var command = new ReceiveStripeWebhookCommand(PAYLOAD + " ", signedHeader(PAYLOAD));

        assertThrows(InvalidStripeWebhookSignature.class, () -> service.handle(command));
        verify(events, never()).insertIfAbsent(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void missingSigningSecretFailsClosed() throws Exception {
        var verifier = new StripeWebhookVerifierImpl("");
        assertThrows(StripeWebhookUnavailable.class, () -> verifier.verify(PAYLOAD, signedHeader(PAYLOAD)));
    }

    @Test
    void commandRequiresRawPayloadAndSignature() {
        assertThrows(IllegalArgumentException.class, () -> new ReceiveStripeWebhookCommand("", "signature"));
        assertThrows(IllegalArgumentException.class, () -> new ReceiveStripeWebhookCommand(PAYLOAD, ""));
    }

    @Test
    void storedEventRequiresCompleteVerifiedData() {
        var receivedAt = Instant.now();
        var event = new StripeWebhookEvent("evt_test", "customer.created", PAYLOAD, receivedAt);

        assertEquals("evt_test", event.getId());
        assertEquals("customer.created", event.getEventType());
        assertEquals(PAYLOAD, event.getPayload());
        assertEquals(receivedAt, event.getReceivedAt());
        assertThrows(IllegalArgumentException.class,
                () -> new StripeWebhookEvent("", "customer.created", PAYLOAD, receivedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new StripeWebhookEvent("evt_test", "", PAYLOAD, receivedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new StripeWebhookEvent("evt_test", "customer.created", "", receivedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new StripeWebhookEvent("evt_test", "customer.created", PAYLOAD, null));
    }

    @Test
    void endpointReturnsExpectedStatusForSignedInvalidAndFailedEvents() throws Exception {
        var events = mock(StripeWebhookEventRepository.class);
        var service = new StripeWebhookEventHandlerImpl(new StripeWebhookVerifierImpl(SECRET), events);
        var mvc = MockMvcBuilders.standaloneSetup(new StripeWebhookController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signedHeader(PAYLOAD))
                        .content(PAYLOAD))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=invalid")
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest());

        when(events.insertIfAbsent("evt_test", "setup_intent.succeeded", PAYLOAD))
                .thenThrow(new IllegalStateException("database unavailable"));
        mvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signedHeader(PAYLOAD))
                        .content(PAYLOAD))
                .andExpect(status().isInternalServerError());
    }

    private String signedHeader(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var signature = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(signature);
    }
}
