package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeByIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeInventoryItemByIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgesQuery;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.domain.services.EdgeQueryService;
import com.resolum.alivia.assets.interfaces.rest.resources.ChangeEdgeStatusResource;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeInventoryItemResource;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeResource;
import com.resolum.alivia.assets.interfaces.rest.resources.InventoryPageResource;
import com.resolum.alivia.assets.interfaces.rest.resources.RegisterEdgeResource;
import com.resolum.alivia.assets.interfaces.rest.resources.UpdateEdgeResource;
import com.resolum.alivia.assets.interfaces.rest.transform.ChangeEdgeStatusCommandFromResourceAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.EdgeResourceFromEntityAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.InventoryResourcesFromViewAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.RegisterEdgeCommandFromResourceAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.UpdateEdgeCommandFromResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    private final Long defaultBusinessId;

    /**
     * @param defaultBusinessId business that owns the registered edges. Today Alivia is the only business; it falls
     *                          back to the HCM default so there is a single setting (the same one the devices use).
     */
    public EdgesController(EdgeCommandService edgeCommandService, EdgeQueryService edgeQueryService,
                           @Value("${alivia.assets.default-business-id:${alivia.hcm.default-business-id}}") Long defaultBusinessId) {
        this.edgeCommandService = edgeCommandService;
        this.edgeQueryService = edgeQueryService;
        this.defaultBusinessId = defaultBusinessId;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un edge en el inventario",
            description = "Da de alta un edge (de micrófono o de actuadores) con su identificador físico (MAC) y su tipo. "
                    + "Queda AVAILABLE: solo está en el inventario del negocio y no se considera asignado a ningún hogar "
                    + "hasta que un trabajo de instalación lo coloque en una instalación.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Edge registrado"),
            @ApiResponse(responseCode = "400", description = "MAC o tipo inválidos"),
            @ApiResponse(responseCode = "404", description = "El negocio no existe"),
            @ApiResponse(responseCode = "409", description = "El identificador físico ya está registrado (por otro edge o por un dispositivo)")
    })
    public EdgeResource registerEdge(@RequestBody @Valid RegisterEdgeResource resource) {
        var edgeId = edgeCommandService.handle(
                RegisterEdgeCommandFromResourceAssembler.toCommandFromResource(resource, defaultBusinessId));
        return edgeQueryService.handle(new GetEdgeByIdQuery(edgeId))
                .map(EdgeResourceFromEntityAssembler::toResourceFromEntity)
                .orElseThrow(() -> new EdgeNotFound(edgeId));
    }

    @GetMapping
    @Operation(summary = "Listar el inventario de edges",
            description = "Página de los edges del negocio, ordenados por id, con la instalación donde está asignado cada uno "
                    + "(nulo si no lo está). Se puede filtrar por estado (status) y por tipo (type); el tamaño de página es de 1 a 200.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de edges"),
            @ApiResponse(responseCode = "400", description = "Estado o tipo desconocidos, página negativa o tamaño fuera de 1 a 200")
    })
    public InventoryPageResource<EdgeInventoryItemResource> getEdges(
            @RequestParam(required = false) String status, @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + InventoryResourcesFromViewAssembler.DEFAULT_PAGE_SIZE) int size) {
        var query = new GetEdgesQuery(defaultBusinessId, InventoryResourcesFromViewAssembler.status(status),
                InventoryResourcesFromViewAssembler.edgeType(type), InventoryResourcesFromViewAssembler.pageable(page, size));
        return InventoryResourcesFromViewAssembler.toPageResource(edgeQueryService.handle(query),
                InventoryResourcesFromViewAssembler::toResource);
    }

    @GetMapping("/{edgeId}")
    @Operation(summary = "Consultar un edge",
            description = "Un edge del inventario, con la instalación donde está asignado (nulo si no lo está).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El edge"),
            @ApiResponse(responseCode = "404", description = "El edge no existe")
    })
    public EdgeInventoryItemResource getEdge(@PathVariable Long edgeId) {
        return edgeQueryService.handle(new GetEdgeInventoryItemByIdQuery(edgeId))
                .map(InventoryResourcesFromViewAssembler::toResource)
                .orElseThrow(() -> new EdgeNotFound(edgeId));
    }

    @PutMapping(value = "/{edgeId}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar un edge",
            description = "Actualiza la MAC y el tipo del edge. Mientras está asignado a una instalación solo puede cambiar la MAC; "
                    + "cambiar el tipo de un edge que no está asignado revoca su credencial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Edge actualizado"),
            @ApiResponse(responseCode = "400", description = "MAC o tipo inválidos"),
            @ApiResponse(responseCode = "404", description = "El edge no existe"),
            @ApiResponse(responseCode = "409", description = "El identificador físico ya está registrado, o se pide cambiar el tipo de un edge asignado")
    })
    public ResponseEntity<EdgeResource> updateEdge(@PathVariable Long edgeId, @RequestBody @Valid UpdateEdgeResource resource) {
        var updateEdgeCommand = UpdateEdgeCommandFromResourceAssembler.toCommandFromResource(edgeId, resource);
        edgeCommandService.handle(updateEdgeCommand);
        return edgeQueryService.handle(new GetEdgeByIdQuery(edgeId))
                .map(edge -> ResponseEntity.ok(EdgeResourceFromEntityAssembler.toResourceFromEntity(edge)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/{edgeId}/status", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Cambiar el estado de un edge",
            description = "Mueve el edge a AVAILABLE, MAINTENANCE, BROKEN o RETIRED siguiendo las transiciones permitidas "
                    + "(RETIRED es definitivo). IN_USE no se puede pedir: un edge pasa a IN_USE solo al colocarlo en una instalación. "
                    + "Un edge asignado a una instalación no puede pasar a AVAILABLE ni a RETIRED hasta que se quite de ella; "
                    + "retirar un edge revoca su credencial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado cambiado"),
            @ApiResponse(responseCode = "400", description = "Estado desconocido o IN_USE"),
            @ApiResponse(responseCode = "404", description = "El edge no existe"),
            @ApiResponse(responseCode = "409", description = "Transición no permitida, o el edge sigue asignado a una instalación")
    })
    public EdgeResource changeEdgeStatus(@PathVariable Long edgeId, @RequestBody @Valid ChangeEdgeStatusResource resource) {
        edgeCommandService.handle(ChangeEdgeStatusCommandFromResourceAssembler.toCommandFromResource(edgeId, resource));
        return edgeQueryService.handle(new GetEdgeByIdQuery(edgeId))
                .map(EdgeResourceFromEntityAssembler::toResourceFromEntity)
                .orElseThrow(() -> new EdgeNotFound(edgeId));
    }
}
