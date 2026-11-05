package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.exceptions.InstallationNotFound;
import com.resolum.alivia.assets.domain.model.queries.GetInstallationEdgeSyncQuery;
import com.resolum.alivia.assets.domain.services.EdgeSyncQueryService;
import com.resolum.alivia.assets.interfaces.rest.resources.InstallationEdgeSyncResource;
import com.resolum.alivia.assets.interfaces.rest.transform.EdgeSyncResourcesFromViewAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/installations/{installationId}/edge-sync", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Sincronización de los edge de una instalación", description = "Qué versión de la tabla de dispositivos ha confirmado cada edge del hogar")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class InstallationEdgeSyncController {
    private final EdgeSyncQueryService queryService;

    public InstallationEdgeSyncController(EdgeSyncQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Ver el retraso de los edge de una instalación",
            description = "Para cada edge asignado a la instalación: la mayor versión que confirmó, cuándo, la última vez que "
                    + "pidió su tabla y cuántas versiones le faltan para la vigente. Un edge que nunca confirmó una versión "
                    + "muestra appliedVersion y behindBy nulos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado de sincronización de los edge de la instalación"),
            @ApiResponse(responseCode = "404", description = "La instalación no existe")
    })
    public InstallationEdgeSyncResource getEdgeSync(@PathVariable Long installationId) {
        return queryService.handle(new GetInstallationEdgeSyncQuery(installationId))
                .map(EdgeSyncResourcesFromViewAssembler::toResourceFromView)
                .orElseThrow(() -> new InstallationNotFound(installationId));
    }
}
