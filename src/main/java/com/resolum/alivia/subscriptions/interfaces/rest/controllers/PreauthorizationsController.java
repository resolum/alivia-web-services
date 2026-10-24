package com.resolum.alivia.subscriptions.interfaces.rest.controllers;

import com.resolum.alivia.subscriptions.domain.model.commands.CreatePreauthorizationCommand;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorQuantity;
import com.resolum.alivia.subscriptions.domain.services.PreauthorizationCommandService;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.PreauthorizationResource;
import com.resolum.alivia.subscriptions.domain.services.PreauthorizationQueryService;
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
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions/preauthorizations")
@Tag(name = "Preautorizaciones", description = "Retención temporal del importe de una contratación, sin cobrar ni activar la suscripción")
public class PreauthorizationsController {
    private final PreauthorizationCommandService service;
    private final PreauthorizationQueryService queryService;
    public PreauthorizationsController(PreauthorizationCommandService service, PreauthorizationQueryService queryService) {
        this.service = service; this.queryService = queryService;
    }

    @Schema(name = "CrearPreautorizacionRequest", description = "Datos de la contratación y método de pago ya registrado en Stripe")
    public record Request(
            @Schema(description = "Identificador del hogar asociado a la contratación", example = "home-123", requiredMode = Schema.RequiredMode.REQUIRED)
            String householdId,
            @Schema(description = "ID numérico de un plan activo", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            Long planId,
            @Schema(description = "Número de habitaciones para calcular el precio en el servidor", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            int rooms,
            @Schema(description = "Tipos y cantidades de actuadores seleccionados", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ActuatorQuantity> actuators,
            @Schema(description = "ID del método de pago de Stripe obtenido tras confirmar el SetupIntent; no envíes datos de tarjeta", example = "pm_123", requiredMode = Schema.RequiredMode.REQUIRED)
            String paymentMethodId) { }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Solicitar la preautorización de una contratación",
            description = "Comprueba el plan y la selección, calcula el importe en el servidor y crea un PaymentIntent de Stripe con captura manual. "
                    + "Crea Subscription y Payment locales. La respuesta puede estar en AWAITING_CONFIRMATION o PREAUTHORIZED; "
                    + "PREAUTHORIZED indica una retención confirmada, todavía sin cobro ni activación de la suscripción. "
                    + "Envía el mismo Idempotency-Key al reintentar la misma operación. Por ahora no se valida que el hogar esté completo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitud creada; revisar el campo status", content = @Content(schema = @Schema(implementation = PreauthorizationResource.class))),
            @ApiResponse(responseCode = "400", description = "Datos o clave de idempotencia inválidos", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No se recibió un usuario autenticado", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Plan no encontrado", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Plan inactivo o contratación en curso para el hogar", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "La tarjeta rechazó la preautorización", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Stripe no está configurado o no está disponible", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public PreauthorizationResource create(@Parameter(hidden = true) Principal principal,
                                          @Parameter(description = "UUID estable para los reintentos de esta operación", required = true)
                                          @RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                          @RequestBody Request request) {
        return PreauthorizationResource.from(service.handle(new CreatePreauthorizationCommand(principal == null ? null : principal.getName(),
                request.householdId(), request.planId(), request.rooms(), request.actuators(), request.paymentMethodId(), idempotencyKey)));
    }

    @GetMapping("/{subscriptionId}")
    @Operation(summary = "Consultar el estado de una preautorización",
            description = "Verifica que la contratación pertenezca al usuario, consulta el PaymentIntent en Stripe y actualiza el estado local. "
                    + "No devuelve el clientSecret ni activa la suscripción.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actual del pago", content = @Content(schema = @Schema(implementation = PreauthorizationResource.class))),
            @ApiResponse(responseCode = "401", description = "No se recibió un usuario autenticado", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Contratación no encontrada", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Stripe no está disponible", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public PreauthorizationResource get(@Parameter(hidden = true) Principal principal,
                                        @Parameter(description = "ID de la contratación devuelto al crear la preautorización", example = "42")
                                        @PathVariable Long subscriptionId) {
        return PreauthorizationResource.from(queryService.get(principal == null ? null : principal.getName(), subscriptionId));
    }
}
