package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.model.queries.GetEdgeByIdQuery;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.domain.services.EdgeQueryService;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeResource;
import com.resolum.alivia.assets.interfaces.rest.resources.UpdateEdgeResource;
import com.resolum.alivia.assets.interfaces.rest.transform.EdgeResourceFromEntityAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.UpdateEdgeCommandFromResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/edges", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Edges", description = "Gestión del inventario de edges del negocio")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class EdgesController {
    private final EdgeCommandService edgeCommandService;
    private final EdgeQueryService edgeQueryService;

    public EdgesController(EdgeCommandService edgeCommandService, EdgeQueryService edgeQueryService) {
        this.edgeCommandService = edgeCommandService;
        this.edgeQueryService = edgeQueryService;
    }

    @PutMapping(value = "/{edgeId}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar un edge", description = "Actualiza la MAC y el tipo del edge.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Edge actualizado"),
            @ApiResponse(responseCode = "400", description = "MAC o tipo inválidos"),
            @ApiResponse(responseCode = "404", description = "El edge no existe"),
            @ApiResponse(responseCode = "409", description = "El identificador físico ya está registrado")
    })
    public ResponseEntity<EdgeResource> updateEdge(@PathVariable Long edgeId, @RequestBody @Valid UpdateEdgeResource resource) {
        var updateEdgeCommand = UpdateEdgeCommandFromResourceAssembler.toCommandFromResource(edgeId, resource);
        edgeCommandService.handle(updateEdgeCommand);
        return edgeQueryService.handle(new GetEdgeByIdQuery(edgeId))
                .map(edge -> ResponseEntity.ok(EdgeResourceFromEntityAssembler.toResourceFromEntity(edge)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
