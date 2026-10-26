package com.resolum.alivia.subscriptions.infrastructure.stripe.service;

import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.exceptions.PreauthorizationUnavailable;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentRejected;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.*;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.exception.CardException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
public class StripePaymentAuthorizationGateway implements PaymentAuthorizationGateway {
    private final String secretKey;
    public StripePaymentAuthorizationGateway(@Value("${stripe.secret-key:}") String secretKey) { this.secretKey = secretKey; }

    @Override
    public StripeAuthorization create(String customerId, String paymentMethodId, long amountInCents, String currency, UUID key) {
        if (secretKey.isBlank()) throw new PreauthorizationUnavailable("Stripe no está configurado.");
        var params = PaymentIntentCreateParams.builder().setCustomer(customerId)
                .setPaymentMethod(paymentMethodId).setAmount(amountInCents).setCurrency(currency.toLowerCase())
                .addPaymentMethodType("card")
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                .setConfirm(true)
                .setDescription("Preautorización temporal de contratación Alivia")
                .putMetadata("alivia_operation", "contract_preauthorization")
                .putMetadata("alivia_request_id", key.toString())
                .addExpand("latest_charge").build();
        try {
            var intent = new StripeClient(secretKey).paymentIntents().create(params,
                    RequestOptions.builder().setIdempotencyKey("preauthorization-" + key).build());
            return map(intent);
        } catch (CardException exception) {
            throw new PaymentRejected();
        } catch (StripeException exception) {
            throw new PreauthorizationUnavailable("Stripe no pudo crear la preautorización.", exception);
        }
    }

    @Override
    public StripeAuthorization retrieve(StripePaymentIntentId id) {
        if (secretKey.isBlank()) throw new PreauthorizationUnavailable("Stripe no está configurado.");
        try { return map(new StripeClient(secretKey).paymentIntents().retrieve(id.value(),
                PaymentIntentRetrieveParams.builder().addExpand("latest_charge").build())); }
        catch (StripeException exception) { throw new PreauthorizationUnavailable("No se pudo consultar la preautorización.", exception); }
    }

    @Override
    public void cancel(StripePaymentIntentId id) {
        if (secretKey.isBlank()) throw new PreauthorizationUnavailable("Stripe no está configurado.");
        try { new StripeClient(secretKey).paymentIntents().cancel(id.value()); }
        catch (StripeException exception) { throw new PreauthorizationUnavailable("No se pudo liberar la retención.", exception); }
    }

    private StripeAuthorization map(PaymentIntent intent) {
        var state = switch (intent.getStatus()) {
            case "requires_capture" -> PaymentStatus.PREAUTHORIZED;
            case "canceled" -> PaymentStatus.CANCELED;
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "requires_payment_method" -> PaymentStatus.PAYMENT_REJECTED;
            default -> PaymentStatus.AWAITING_CONFIRMATION;
        };
        Instant captureBefore = null;
        if (intent.getLatestChargeObject() != null && intent.getLatestChargeObject().getPaymentMethodDetails() != null
                && intent.getLatestChargeObject().getPaymentMethodDetails().getCard() != null
                && intent.getLatestChargeObject().getPaymentMethodDetails().getCard().getCaptureBefore() != null) {
            captureBefore = Instant.ofEpochSecond(intent.getLatestChargeObject().getPaymentMethodDetails().getCard().getCaptureBefore());
        }
        return new StripeAuthorization(new StripePaymentIntentId(intent.getId()), intent.getClientSecret(), state, captureBefore);
    }
}
