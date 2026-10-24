package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.PreauthorizationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "PreautorizacionResource", description = "Estado de la retención temporal asociada a una contratación")
public record PreauthorizationResource(
        @Schema(description = "ID local de la contratación", example = "42") Long subscriptionId,
        @Schema(description = "ID local del pago", example = "17") Long paymentId,
        @Schema(description = "AWAITING_CONFIRMATION, PREAUTHORIZED, PAYMENT_REJECTED o CANCELED", example = "PREAUTHORIZED") String status,
        @Schema(description = "ID del PaymentIntent en Stripe", example = "pi_123") String stripePaymentIntentId,
        @Schema(description = "Se devuelve al crear o reintentar la solicitud; la consulta de estado lo omite", nullable = true) String clientSecret,
        @Schema(description = "Límite de captura informado por Stripe, si está disponible", nullable = true) Instant captureBefore) {
    public static PreauthorizationResource from(PreauthorizationResult result) {
        return new PreauthorizationResource(result.subscriptionId().value(), result.paymentId().value(),
                result.status().name(), result.stripePaymentIntentId(), result.clientSecret(), result.captureBefore());
    }
}
