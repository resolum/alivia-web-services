package com.resolum.alivia.subscriptions.interfaces.rest.controllers;

import com.resolum.alivia.subscriptions.domain.model.commands.PreparePaymentSetupCommand;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPaymentSetupStatusQuery;
import com.resolum.alivia.subscriptions.domain.services.PaymentSetupCommandService;
import com.resolum.alivia.subscriptions.domain.services.PaymentSetupQueryService;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.PaymentSetupResource;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.PaymentSetupStatusResource;
import com.resolum.alivia.subscriptions.interfaces.rest.transform.PaymentSetupResourceFromResultsAssembler;
import com.resolum.alivia.subscriptions.interfaces.rest.transform.PaymentSetupStatusResourceFromResultsAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/payments", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Pagos", description = "Preparación segura del registro de tarjetas con Stripe")
public class PaymentsController {
    private final PaymentSetupCommandService commandService;
    private final PaymentSetupQueryService queryService;
    public PaymentsController(PaymentSetupCommandService commandService, PaymentSetupQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Preparar el registro de una tarjeta sin cobrar",
            description = "Crea un SetupIntent de Stripe para usar con Payment Element. "
                    + "La misma clave de idempotencia permite reintentar la petición. "
                    + "No crea una suscripción ni recibe datos de tarjeta.")
    @ApiResponse(responseCode = "201", description = "Autorización temporal creada")
    @ApiResponse(responseCode = "400", description = "Clave de idempotencia ausente o inválida")
    @ApiResponse(responseCode = "401", description = "Usuario no autenticado")
    @ApiResponse(responseCode = "503", description = "Stripe o su configuración no están disponibles")
    public PaymentSetupResource prepareSetup(
            Principal principal,
            @Parameter(description = "UUID estable para los reintentos de esta operación", required = true)
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {
        return PaymentSetupResourceFromResultsAssembler.toResourceFromResult(
                commandService.handle(new PreparePaymentSetupCommand(principal == null ? null : principal.getName(), idempotencyKey)));
    }

    @GetMapping("/setups/{setupIntentId}")
    @Operation(summary = "Consultar el estado del registro de una tarjeta",
            description = "Verifica en Stripe el estado del SetupIntent y que pertenezca al usuario. "
                    + "No expone el clientSecret ni los datos de la tarjeta.")
    @ApiResponse(responseCode = "200", description = "Estado del registro")
    @ApiResponse(responseCode = "401", description = "Usuario no autenticado")
    @ApiResponse(responseCode = "404", description = "SetupIntent no encontrado para este usuario")
    @ApiResponse(responseCode = "503", description = "Stripe no está disponible")
    public PaymentSetupStatusResource getSetupStatus(Principal principal,
                                                     @PathVariable String setupIntentId) {
        return PaymentSetupStatusResourceFromResultsAssembler.toResourceFromResult(
                queryService.handle(new GetPaymentSetupStatusQuery(
                        principal == null ? null : principal.getName(), setupIntentId)));
    }
}
