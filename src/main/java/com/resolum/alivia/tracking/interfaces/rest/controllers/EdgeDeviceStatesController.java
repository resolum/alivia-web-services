package com.resolum.alivia.tracking.interfaces.rest.controllers;

import com.resolum.alivia.tracking.domain.services.ReportDeviceStatesCommandService;
import com.resolum.alivia.tracking.interfaces.rest.resources.DeviceStatesOutcomeResource;
import com.resolum.alivia.tracking.interfaces.rest.resources.ReportDeviceStatesResource;
import com.resolum.alivia.tracking.interfaces.rest.transform.EdgeIdFromAuthentication;
import com.resolum.alivia.tracking.interfaces.rest.transform.ReportDeviceStatesCommandFromResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/** Where an edge service reports how its devices are, which is also its heartbeat. */
@RestController
@RequestMapping(value = "/api/v1/edge/device-states", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Edge: estado de los dispositivos", description = "Estado de los dispositivos y latido de los edge services")
// IAM gives the authenticated edge services these roles; no user has them.
@PreAuthorize("hasAnyRole('EDGE_MICROPHONE', 'EDGE_ACTUATOR')")
public class EdgeDeviceStatesController {
    private final ReportDeviceStatesCommandService service;

    public EdgeDeviceStatesController(ReportDeviceStatesCommandService service) {
        this.service = service;
    }

    @PutMapping(consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Informar el estado de los dispositivos (y el latido del edge)",
            description = "Aplica el estado de cada dispositivo como el último estado confiable y registra que el edge "
                    + "está vivo; sin dispositivos es solo el latido. Responde, para cada dispositivo, APPLIED, STALE (se "
                    + "observó antes del estado ya guardado: no lo reemplaza) o REJECTED con su motivo (conserva el estado "
                    + "confiable). Un cambio real de estado agrega sus eventos al historial. Solo se responde por lo que quedó "
                    + "guardado: si no se puede guardar, 503 y el edge reenvía el mismo reporte.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado de cada dispositivo, en el mismo orden"),
            @ApiResponse(responseCode = "400", description = "Cuerpo ilegible o con demasiados dispositivos"),
            @ApiResponse(responseCode = "401", description = "Credencial inválida, desconocida o revocada"),
            @ApiResponse(responseCode = "404", description = "El edge no está asignado a una instalación activa"),
            @ApiResponse(responseCode = "503", description = "No se pudo guardar; reintentar con el mismo reporte (Retry-After)")
    })
    public ResponseEntity<DeviceStatesOutcomeResource> report(@Valid @RequestBody ReportDeviceStatesResource resource,
                                                              @Parameter(hidden = true) Authentication authentication) {
        var edgeId = EdgeIdFromAuthentication.of(authentication);
        var outcomes = service.handle(ReportDeviceStatesCommandFromResourceAssembler.toCommand(edgeId, resource));
        return ResponseEntity.ok(ReportDeviceStatesCommandFromResourceAssembler.toResource(outcomes));
    }
}
