package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.model.commands.AcknowledgeEdgeVersionCommand;
import com.resolum.alivia.assets.domain.services.EdgeSyncCommandService;
import com.resolum.alivia.assets.interfaces.rest.resources.AcknowledgeEdgeVersionResource;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeSyncStateResource;
import com.resolum.alivia.assets.interfaces.rest.transform.EdgeIdFromAuthenticationAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.EdgeSyncResourcesFromViewAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Where an edge service says which version of its table it applied. The resource is the synchronization state of the
 * calling edge: putting a version replaces it, and the server keeps the higher of the two, so repeating a report or
 * sending a late one changes nothing.
 */
@RestController
@RequestMapping(value = "/api/v1/edge/sync-state", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Edge: confirmación de sincronización", description = "Versión de la tabla de dispositivos que cada edge service confirma haber aplicado")
// IAM gives the authenticated edge services these roles; no user has them.
@PreAuthorize("hasAnyRole('EDGE_MICROPHONE', 'EDGE_ACTUATOR')")
public class EdgeSyncStateController {
    private final EdgeSyncCommandService syncService;

    public EdgeSyncStateController(EdgeSyncCommandService syncService) {
        this.syncService = syncService;
    }

    @PutMapping(consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirmar la versión de la tabla aplicada",
            description = "El edge informa de la mayor versión de su tabla de dispositivos que ya aplicó. Es idempotente y "
                    + "monótona: el servidor conserva la mayor, así que repetir el informe o enviar uno tardío no cambia "
                    + "nada. Responde con lo que quedó guardado, que puede ser una versión mayor que la enviada. Sirve para que el "
                    + "negocio vea el retraso de cada edge; el servidor no decide con ello qué enviar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado guardado"),
            @ApiResponse(responseCode = "400", description = "La versión falta o es negativa"),
            @ApiResponse(responseCode = "401", description = "Credencial inválida, desconocida o revocada"),
            @ApiResponse(responseCode = "404", description = "El edge no está asignado a una instalación activa"),
            @ApiResponse(responseCode = "422", description = "La tabla de la instalación aún no ha llegado a esa versión")
    })
    public ResponseEntity<EdgeSyncStateResource> acknowledge(@Valid @RequestBody AcknowledgeEdgeVersionResource resource,
                                                            @Parameter(hidden = true) Authentication authentication) {
        var edgeId = EdgeIdFromAuthenticationAssembler.toEdgeId(authentication);
        var stored = syncService.handle(new AcknowledgeEdgeVersionCommand(edgeId, resource.appliedVersion()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(EdgeSyncResourcesFromViewAssembler.toResourceFromView(stored));
    }
}
