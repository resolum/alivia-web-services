package com.resolum.alivia.tracking.interfaces.rest.controllers;

import com.resolum.alivia.tracking.domain.model.commands.ReportEventBatchCommand;
import com.resolum.alivia.tracking.domain.services.RecordEdgeEventsCommandService;
import com.resolum.alivia.tracking.interfaces.rest.resources.EventBatchOutcomeResource;
import com.resolum.alivia.tracking.interfaces.rest.resources.ReportEdgeEventsBatchResource;
import com.resolum.alivia.tracking.interfaces.rest.transform.EdgeIdFromAuthentication;
import com.resolum.alivia.tracking.interfaces.rest.transform.ReportedEventFromBatchResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/** Where an edge service sends, in order, the events it has: the ones of now and the ones it kept without connection. */
@RestController
@RequestMapping(value = "/api/v1/edge/events/batch", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Edge: eventos del hogar", description = "Sincronización por lotes de los eventos de los edge services")
// IAM gives the authenticated edge services these roles; no user has them.
@PreAuthorize("hasAnyRole('EDGE_MICROPHONE', 'EDGE_ACTUATOR')")
public class EdgeEventsBatchController {
    private final RecordEdgeEventsCommandService eventService;

    public EdgeEventsBatchController(RecordEdgeEventsCommandService eventService) {
        this.eventService = eventService;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Sincronizar un lote de eventos",
            description = "Guarda los eventos en el orden recibido y responde, para cada uno, ACCEPTED (guardado ahora), "
                    + "DUPLICATE (ya estaba guardado: el edge lo da por sincronizado) o REJECTED con su motivo. Un evento "
                    + "inválido no impide los demás. Es idempotente por (edge, eventId), también frente a POST /edge/events. "
                    + "Solo se responde por lo que quedó guardado: si no se puede guardar, 503 y el edge reenvía el lote.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado de cada evento, en el mismo orden"),
            @ApiResponse(responseCode = "400", description = "Cuerpo ilegible, lote vacío o con demasiados eventos (batch_too_large)"),
            @ApiResponse(responseCode = "401", description = "Credencial inválida, desconocida o revocada"),
            @ApiResponse(responseCode = "404", description = "El edge no está asignado a una instalación activa"),
            @ApiResponse(responseCode = "503", description = "No se pudieron guardar; reintentar con el mismo lote (Retry-After)")
    })
    public ResponseEntity<EventBatchOutcomeResource> sync(@Valid @RequestBody ReportEdgeEventsBatchResource resource,
                                                          @Parameter(hidden = true) Authentication authentication) {
        var edgeId = EdgeIdFromAuthentication.of(authentication);
        var outcomes = eventService.handle(new ReportEventBatchCommand(edgeId,
                resource.events().stream().map(ReportedEventFromBatchResourceAssembler::toReportedEvent).toList()));
        return ResponseEntity.ok(ReportedEventFromBatchResourceAssembler.toResource(outcomes));
    }
}
