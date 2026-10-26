package com.resolum.alivia.subscriptions.infrastructure.stripe.service;

import com.resolum.alivia.subscriptions.application.outboundservices.StripeService;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnavailable;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupNotFound;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetup;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetupStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.UserId;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.Customer;
import com.stripe.model.SetupIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StripeServiceImpl implements StripeService {
    private final String secretKey;
    private final String publishableKey;

    public StripeServiceImpl(@Value("${stripe.secret-key:}") String secretKey,
                                     @Value("${stripe.publishable-key:}") String publishableKey) {
        this.secretKey = secretKey;
        this.publishableKey = publishableKey;
    }

    @Override
    public String createCustomer(UserId userId) {
        requireKeys();
        var params = CustomerCreateParams.builder()
                .putMetadata("alivia_user_id", userId.value().toString())
                .build();
        var options = RequestOptions.builder()
                .setIdempotencyKey("alivia-user-customer-" + userId.value())
                .build();
        try {
            Customer customer = new StripeClient(secretKey).customers().create(params, options);
            return customer.getId();
        } catch (StripeException exception) {
            throw new PaymentSetupUnavailable("No fue posible crear el cliente de Stripe para este usuario.");
        }
    }

    @Override
    public String createCheckoutCustomer(String configurationDraftId) {
        requireKeys();
        if (configurationDraftId == null || configurationDraftId.isBlank()) {
            throw new IllegalArgumentException("Se requiere el borrador de configuración.");
        }
        var params = CustomerCreateParams.builder()
                .setDescription("Contratación Alivia en curso")
                .putMetadata("alivia_configuration_draft_id", configurationDraftId)
                .putMetadata("alivia_customer_stage", "onboarding")
                .build();
        var options = RequestOptions.builder()
                .setIdempotencyKey("alivia-configuration-customer-" + configurationDraftId)
                .build();
        try {
            return new StripeClient(secretKey).customers().create(params, options).getId();
        } catch (StripeException exception) {
            throw new PaymentSetupUnavailable("No fue posible preparar el cliente de Stripe para la contratación.");
        }
    }

    @Override
    public PaymentSetup createPaymentSetup(String customerId, UUID idempotencyKey) {
        requireKeys();
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Se requiere el Customer de Stripe del usuario.");
        }
        var params = SetupIntentCreateParams.builder()
                .setCustomer(customerId)
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                .addPaymentMethodType("card")
                .setDescription("Registro de tarjeta para contratación Alivia")
                .putMetadata("alivia_operation", "payment_method_setup")
                .build();
        var requestOptions = RequestOptions.builder()
                .setIdempotencyKey("payment-setup-" + customerId + "-" + idempotencyKey)
                .build();
        try {
            SetupIntent intent = new StripeClient(secretKey).setupIntents().create(params, requestOptions);
            return new PaymentSetup(intent.getId(), intent.getClientSecret(), publishableKey);
        } catch (StripeException exception) {
            throw new PaymentSetupUnavailable("No fue posible preparar el registro de la tarjeta en Stripe. Reintenta con la misma clave.");
        }
    }

    @Override
    public PaymentSetupStatus retrievePaymentSetup(String setupIntentId) {
        requireKeys();
        try {
            SetupIntent intent = new StripeClient(secretKey).setupIntents().retrieve(setupIntentId);
            return new PaymentSetupStatus(intent.getId(), intent.getCustomer(), intent.getStatus(), intent.getPaymentMethod());
        } catch (InvalidRequestException exception) {
            throw new PaymentSetupNotFound();
        } catch (StripeException exception) {
            throw new PaymentSetupUnavailable("No fue posible consultar el registro de la tarjeta en Stripe.");
        }
    }

    private void requireKeys() {
        if (secretKey.isBlank() || publishableKey.isBlank()) {
            throw new PaymentSetupUnavailable("Las claves de Stripe aún no están configuradas.");
        }
    }
}
