package com.resolum.alivia.tracking.interfaces.rest.controllers;

import com.resolum.alivia.tracking.domain.model.queries.GetDeviceTelemetryQuery;
import com.resolum.alivia.tracking.domain.services.DeviceTelemetryQueryService;
import com.resolum.alivia.tracking.interfaces.rest.resources.DeviceTelemetryPageResource;
import com.resolum.alivia.tracking.interfaces.rest.transform.DeviceTelemetryPageResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/** The diagnostics readings of a device, for the staff who look after the installations. */
@RestController
@RequestMapping(value = "/api/v1/devices/{deviceId}/telemetry", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Seguimiento: telemetría técnica", description = "Lecturas de diagnóstico de un dispositivo (señal, memoria…)")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
public class DeviceTelemetryController {
    private final DeviceTelemetryQueryService queryService;

    public DeviceTelemetryController(DeviceTelemetryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Telemetría técnica de un dispositivo",
            description = "Lecturas de diagnóstico del dispositivo en el hogar donde está colocado ahora, de la más "
                    + "reciente a la más antigua. Solo para administradores y técnicos. from se incluye y to se excluye "
                    + "(ISO-8601, UTC); sin to es ahora y sin from es el período máximo hacia atrás desde to. page empieza "
                    + "en 0. Las lecturas se guardan submuestreadas (a lo sumo una cada 5 minutos por dispositivo).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de lecturas"),
            @ApiResponse(responseCode = "400", description = "Período o página inválidos (código en el cuerpo)"),
            @ApiResponse(responseCode = "401", description = "Sin sesión"),
            @ApiResponse(responseCode = "403", description = "La cuenta no es administrador ni técnico"),
            @ApiResponse(responseCode = "404", description = "El dispositivo no existe o no está colocado en un hogar"),
            @ApiResponse(responseCode = "503", description = "No se pudo leer; reintentar (Retry-After)")
    })
    public ResponseEntity<DeviceTelemetryPageResource> telemetry(
            @PathVariable Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var view = queryService.handle(new GetDeviceTelemetryQuery(deviceId, from, to, page, size));
        return ResponseEntity.ok(DeviceTelemetryPageResourceAssembler.toResource(view));
    }
}
