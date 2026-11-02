package com.resolum.alivia.subscriptions.interfaces.rest.controllers;

import com.resolum.alivia.subscriptions.application.internal.commandservices.ConfigurationDraftPaymentService;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.ConfigurationDraftPreauthorizationResource;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.PaymentSetupResource;
import com.resolum.alivia.subscriptions.interfaces.rest.transform.PaymentSetupResourceFromResultsAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/configuration-drafts/{draftId}", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Pago de contratación", description = "Registro y preautorización de pago durante el onboarding, antes de crear la cuenta")
public class ConfigurationDraftPaymentsController {
    private final ConfigurationDraftPaymentService payments;

    public ConfigurationDraftPaymentsController(ConfigurationDraftPaymentService payments) {
        this.payments = payments;
    }

    public record PreauthorizationRequest(
            @Schema(description = "Método de pago generado por Stripe Payment Element", example = "pm_123")
            String paymentMethodId) { }

    @PostMapping("/payment-setup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Preparar el formulario de pago del borrador",
            description = "Crea el Customer y SetupIntent de Stripe vinculados al borrador. No requiere inicio de sesión y no realiza cobros.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment Element preparado"),
            @ApiResponse(responseCode = "404", description = "Borrador inexistente", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Stripe no está disponible", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public PaymentSetupResource prepare(
            @Parameter(description = "UUID del borrador") @PathVariable String draftId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {
        return PaymentSetupResourceFromResultsAssembler.toResourceFromResult(payments.prepare(draftId, idempotencyKey));
    }

    @PostMapping(value = "/preauthorization", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Preautorizar el importe del borrador",
            description = "Recalcula el total desde el plan y las habitaciones guardadas, y crea un PaymentIntent con captura manual. No crea una cuenta ni activa la suscripción.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Retención solicitada"),
            @ApiResponse(responseCode = "400", description = "Configuración o método de pago inválidos", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Borrador inexistente", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "La tarjeta rechazó la preautorización", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ConfigurationDraftPreauthorizationResource preauthorize(
            @PathVariable String draftId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody PreauthorizationRequest request) {
        return ConfigurationDraftPreauthorizationResource.from(draftId,
                payments.preauthorize(draftId, request.paymentMethodId(), idempotencyKey));
    }

    @GetMapping("/preauthorization")
    @Operation(summary = "Consultar la preautorización del borrador")
    public ConfigurationDraftPreauthorizationResource status(@PathVariable String draftId) {
        return ConfigurationDraftPreauthorizationResource.from(draftId, payments.status(draftId));
    }
}
