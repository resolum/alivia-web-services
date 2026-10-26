package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripeAuthorization;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "PreautorizacionBorrador", description = "Retención temporal vinculada al onboarding, todavía sin cuenta ni suscripción activa")
public record ConfigurationDraftPreauthorizationResource(
        @Schema(description = "Borrador propietario de la operación") String draftId,
        @Schema(description = "Estado de la retención", example = "PREAUTHORIZED") String status,
        @Schema(description = "PaymentIntent visible en Stripe", example = "pi_123") String stripePaymentIntentId,
        @Schema(description = "Secreto usado únicamente para completar acciones adicionales", nullable = true) String clientSecret,
        @Schema(description = "Fecha límite de captura informada por Stripe", nullable = true) Instant captureBefore) {

    public static ConfigurationDraftPreauthorizationResource from(String draftId, StripeAuthorization authorization) {
        return new ConfigurationDraftPreauthorizationResource(draftId, authorization.state().name(),
                authorization.intentId().value(), authorization.clientSecret(), authorization.captureBefore());
    }
}
