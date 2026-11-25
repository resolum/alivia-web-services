package com.resolum.alivia.tracking.interfaces.rest.controllers;

import com.resolum.alivia.tracking.domain.model.queries.GetHomeStatusByAssistedPersonQuery;
import com.resolum.alivia.tracking.domain.services.HomeStatusQueryService;
import com.resolum.alivia.tracking.interfaces.rest.resources.HomeStatusResource;
import com.resolum.alivia.tracking.interfaces.rest.transform.HomeStatusResourceAssembler;
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
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/** What the family sees of the devices of the home of an assisted person. */
@RestController
@RequestMapping(value = "/api/v1/assisted-persons/{personId}/devices/status", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Seguimiento: estado de los dispositivos", description = "Estado actual y último estado conocido de los dispositivos del hogar")
public class HomeStatusController {
    private final HomeStatusQueryService queryService;

    public HomeStatusController(HomeStatusQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Estado de los dispositivos del hogar de una persona asistida",
            description = "Lista todos los dispositivos colocados en el hogar con su último estado confiable. Uno que "
                    + "nunca reportó figura con connectionStatus UNKNOWN. Si el edge de un dispositivo no se escucha, "
                    + "conserva su último estado confirmado y se marca stale=true (no se infiere que esté desconectado). "
                    + "edgeLink resume el hogar; offlineSince, lastUpdatedAt y mayHavePendingEvents dicen desde cuándo y "
                    + "qué tan viejo es lo que se ve. Lo puede leer quien pertenece a la red de cuidado de la persona.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado de los dispositivos del hogar"),
            @ApiResponse(responseCode = "401", description = "Sin sesión"),
            @ApiResponse(responseCode = "403", description = "La cuenta no es de un usuario"),
            @ApiResponse(responseCode = "404", description = "La persona no existe o no puede leerla (mismo código), o aún no tiene hogar"),
            @ApiResponse(responseCode = "503", description = "No se pudo leer el estado; reintentar (Retry-After)")
    })
    public ResponseEntity<HomeStatusResource> status(@PathVariable Long personId,
                                                     @Parameter(hidden = true) Authentication authentication) {
        var view = queryService.handle(new GetHomeStatusByAssistedPersonQuery(
                UserIdFromAuthentication.of(authentication), personId));
        return ResponseEntity.ok(HomeStatusResourceAssembler.toResource(personId, view));
    }
}
