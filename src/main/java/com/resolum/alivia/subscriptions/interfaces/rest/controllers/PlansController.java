package com.resolum.alivia.subscriptions.interfaces.rest.controllers;

import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.domain.model.queries.GetAllPlansQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.QuotePlanQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorQuantities;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanStatus;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.PlanQuoteResource;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.PlanResource;
import com.resolum.alivia.subscriptions.interfaces.rest.transform.PlanQuoteResourceFromResultsAssembler;
import com.resolum.alivia.subscriptions.interfaces.rest.transform.PlanResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/plans", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Planes", description = "Consulta del catálogo, el detalle de cada plan y su precio mensual calculado")
public class PlansController {
    private final PlanQueryService queryService;

    public PlansController(PlanQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Listar planes activos",
            description = "Devuelve el catálogo de planes disponibles con sus precios base, características "
                    + "y límites. No calcula el precio de una selección concreta.")
    @ApiResponse(responseCode = "200", description = "Lista de planes activos")
    @ApiResponse(responseCode = "400", description = "Se solicitó un estado distinto de ACTIVE",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public List<PlanResource> getPlans(
            @Parameter(description = "Estado del catálogo; por ahora solo se admite ACTIVE", example = "ACTIVE")
            @RequestParam(defaultValue = "ACTIVE") PlanStatus status) {
        return queryService.handle(new GetAllPlansQuery(status)).stream()
                .map(PlanResourceFromEntityAssembler::toResourceFromEntity).toList();
    }

    @GetMapping("/{code}")
    @Operation(summary = "Consultar un plan por código",
            description = "Devuelve el detalle del Plan Base (MINIMUM) o del Plan Personalizado (CUSTOM), "
                    + "incluidas sus tarifas y condiciones. No calcula una cotización.")
    @ApiResponse(responseCode = "200", description = "Detalle del plan activo")
    @ApiResponse(responseCode = "404", description = "No existe un plan activo con ese código",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public PlanResource getPlan(
            @Parameter(description = "Código del plan: MINIMUM o CUSTOM", example = "CUSTOM")
            @PathVariable String code) {
        return PlanResourceFromEntityAssembler.toResourceFromEntity(
                queryService.handle(new GetPlanByCodeQuery(PlanCode.from(code))));
    }

    @GetMapping("/{code}/pricing")
    @Operation(summary = "Calcular el precio mensual de un plan",
            description = "Devuelve una cotización desglosada según habitaciones y actuadores. "
                    + "No crea una suscripción ni realiza un cobro. El Plan Base admite una habitación "
                    + "y un actuador incluido; el Personalizado exige al menos dos habitaciones "
                    + "y entre uno y cuatro actuadores.")
    @ApiResponse(responseCode = "200", description = "Cotización mensual con importe base, recargos y total")
    @ApiResponse(responseCode = "400", description = "Cantidad de habitaciones o actuadores no permitida",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No existe un plan activo con ese código",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public PlanQuoteResource getPricing(
            @Parameter(description = "Código del plan: MINIMUM o CUSTOM", example = "CUSTOM")
            @PathVariable String code,
            @Parameter(description = "Habitaciones; si se omite, se usa el mínimo del plan", example = "3")
            @RequestParam(required = false) Integer rooms,
            @Parameter(description = "Cantidad de focos o dispositivos de luz", example = "2")
            @RequestParam(defaultValue = "0") int lights,
            @Parameter(description = "Cantidad de dispositivos de puerta", example = "1")
            @RequestParam(defaultValue = "0") int doors,
            @Parameter(description = "Cantidad de dispositivos de ventana", example = "0")
            @RequestParam(defaultValue = "0") int windows,
            @Parameter(description = "Cantidad de dispositivos de cortina", example = "0")
            @RequestParam(defaultValue = "0") int curtains
    ) {
        var quote = queryService.handle(new QuotePlanQuery(PlanCode.from(code), rooms,
                new ActuatorQuantities(lights, doors, windows, curtains)));
        return PlanQuoteResourceFromResultsAssembler.toResourceFromResult(quote);
    }
}
