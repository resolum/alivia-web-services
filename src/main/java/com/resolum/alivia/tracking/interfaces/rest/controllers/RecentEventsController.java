package com.resolum.alivia.tracking.interfaces.rest.controllers;

import com.resolum.alivia.tracking.domain.model.queries.GetRecentEventsByAssistedPersonQuery;
import com.resolum.alivia.tracking.domain.services.RecentEventsQueryService;
import com.resolum.alivia.tracking.interfaces.rest.resources.RecentEventsResource;
import com.resolum.alivia.tracking.interfaces.rest.transform.RecentEventsResourceAssembler;
import com.resolum.alivia.tracking.interfaces.rest.transform.UserIdFromAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/** What happened lately in the home of an assisted person. */
@RestController
@RequestMapping(value = "/api/v1/assisted-persons/{personId}/events/recent", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Seguimiento: eventos recientes", description = "Acciones y alertas recientes del hogar de una persona asistida")
public class RecentEventsController {
    private final RecentEventsQueryService queryService;

    public RecentEventsController(RecentEventsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Eventos recientes del hogar de una persona asistida",
            description = "Los últimos eventos del hogar, del más reciente al más antiguo: qué se pidió, qué hicieron los "
                    + "dispositivos, las alertas y las pérdidas de conexión de los equipos, con o sin dispositivo. limit por "
                    + "defecto 20, máximo 100. Lo puede leer quien pertenece a la red de cuidado de la persona.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos recientes"),
            @ApiResponse(responseCode = "400", description = "limit inválido (invalid_limit) o no numérico"),
            @ApiResponse(responseCode = "401", description = "Sin sesión"),
            @ApiResponse(responseCode = "403", description = "La cuenta no es de un usuario"),
            @ApiResponse(responseCode = "404", description = "La persona no existe o no puede leerla (mismo código), o aún no tiene hogar"),
            @ApiResponse(responseCode = "503", description = "No se pudo leer; reintentar (Retry-After)")
    })
    public ResponseEntity<RecentEventsResource> recent(@PathVariable Long personId,
                                                       @RequestParam(required = false) Integer limit,
                                                       @Parameter(hidden = true) Authentication authentication) {
        var view = queryService.handle(new GetRecentEventsByAssistedPersonQuery(
                UserIdFromAuthentication.of(authentication), personId, limit));
        return ResponseEntity.ok(RecentEventsResourceAssembler.toResource(personId, view));
    }
}
