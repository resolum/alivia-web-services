package com.resolum.alivia.tracking.interfaces.rest.controllers;

import com.resolum.alivia.tracking.domain.model.queries.GetDeviceEventsQuery;
import com.resolum.alivia.tracking.domain.services.DeviceEventsQueryService;
import com.resolum.alivia.tracking.interfaces.rest.resources.DeviceEventsPageResource;
import com.resolum.alivia.tracking.interfaces.rest.transform.DeviceEventsPageResourceAssembler;
import com.resolum.alivia.tracking.interfaces.rest.transform.UserIdFromAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/** The history of what happened with a device of the home. */
@RestController
@RequestMapping(value = "/api/v1/devices/{deviceId}/events", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Seguimiento: historial de un dispositivo", description = "Eventos de un dispositivo del hogar, del más reciente al más antiguo")
public class DeviceEventsController {
    private final DeviceEventsQueryService queryService;

    public DeviceEventsController(DeviceEventsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Historial de eventos de un dispositivo",
            description = "Eventos del dispositivo en el hogar donde está colocado ahora, del más reciente al más antiguo. "
                    + "from se incluye y to se excluye (ISO-8601, UTC); sin to es ahora y sin from es el período máximo "
                    + "hacia atrás desde to. page empieza en 0. Lo que le pasó al dispositivo en un hogar anterior no se muestra.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página del historial"),
            @ApiResponse(responseCode = "400", description = "Período, página o tipo inválidos (código en el cuerpo)"),
            @ApiResponse(responseCode = "401", description = "Sin sesión"),
            @ApiResponse(responseCode = "403", description = "La cuenta no es de un usuario"),
            @ApiResponse(responseCode = "404", description = "El dispositivo no existe o la cuenta no puede leerlo (mismo código)"),
            @ApiResponse(responseCode = "503", description = "No se pudo leer; reintentar (Retry-After)")
    })
    public ResponseEntity<DeviceEventsPageResource> history(
            @PathVariable Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @Parameter(hidden = true) Authentication authentication) {
        var view = queryService.handle(new GetDeviceEventsQuery(UserIdFromAuthentication.of(authentication), deviceId,
                from, to, type, page, size));
        return ResponseEntity.ok(DeviceEventsPageResourceAssembler.toResource(view));
    }
}
