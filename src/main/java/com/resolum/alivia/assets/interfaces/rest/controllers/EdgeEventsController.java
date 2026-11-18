package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.model.commands.ReportEdgeEventCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeEventType;
import com.resolum.alivia.assets.domain.services.EdgeEventCommandService;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeEventReportResource;
import com.resolum.alivia.assets.interfaces.rest.resources.ReportEdgeEventResource;
import com.resolum.alivia.assets.interfaces.rest.transform.EdgeIdFromAuthenticationAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/** Where an edge service reports what happens in its home, so the relatives in charge are notified. */
@RestController
@RequestMapping(value = "/api/v1/edge/events", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Edge: eventos del hogar", description = "Pedidos de ayuda, batería baja y fallas que informan los edge services")
// IAM gives the authenticated edge services these roles; no user has them.
@PreAuthorize("hasAnyRole('EDGE_MICROPHONE', 'EDGE_ACTUATOR')")
public class EdgeEventsController {
    private final EdgeEventCommandService eventService;

    public EdgeEventsController(EdgeEventCommandService eventService) {
        this.eventService = eventService;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Informar un evento del hogar",
            description = "Notifica a los familiares a cargo del hogar del edge (responsabilidad PRINCIPAL). Un pedido de "
                    + "ayuda y una falla llegan como alerta urgente. Es idempotente por eventId: reenviar el mismo evento no "
                    + "vuelve a notificar.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Evento recibido"),
            @ApiResponse(responseCode = "400", description = "Tipo desconocido o datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Credencial inválida, desconocida o revocada"),
            @ApiResponse(responseCode = "404", description = "El edge no está asignado a una instalación activa")
    })
    public ResponseEntity<EdgeEventReportResource> report(@Valid @RequestBody ReportEdgeEventResource resource,
                                                         @Parameter(hidden = true) Authentication authentication) {
        var edgeId = EdgeIdFromAuthenticationAssembler.toEdgeId(authentication);
        var type = EdgeEventType.valueOf(resource.type().trim().toUpperCase(Locale.ROOT));
        var notified = eventService.handle(new ReportEdgeEventCommand(edgeId, resource.eventId(), type,
                resource.deviceId(), resource.batteryPct(), resource.detail()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new EdgeEventReportResource(notified));
    }
}
