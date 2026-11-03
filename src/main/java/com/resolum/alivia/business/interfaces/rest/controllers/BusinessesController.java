package com.resolum.alivia.business.interfaces.rest.controllers;

import com.resolum.alivia.business.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.business.domain.model.queries.GetBusinessByAdministratorIdQuery;
import com.resolum.alivia.business.domain.model.queries.GetBusinessByIdQuery;
import com.resolum.alivia.business.domain.services.BusinessCommandService;
import com.resolum.alivia.business.domain.services.BusinessQueryService;
import com.resolum.alivia.business.interfaces.rest.resources.BusinessResource;
import com.resolum.alivia.business.interfaces.rest.resources.CreateBusinessResource;
import com.resolum.alivia.business.interfaces.rest.resources.UpdateBusinessResource;
import com.resolum.alivia.business.interfaces.rest.transform.BusinessResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/businesses", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Negocios", description = "Gestión del negocio central y su perfil corporativo")
public class BusinessesController {

    private final BusinessCommandService commandService;
    private final BusinessQueryService queryService;

    public BusinessesController(BusinessCommandService commandService, BusinessQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un negocio")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Negocio creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "404", description = "Administrador no encontrado"),
            @ApiResponse(responseCode = "409", description = "Conflicto por duplicidad (RUC, nombres, email o administrador ya asignado)")
    })
    public ResponseEntity<BusinessResource> createBusiness(@Valid @RequestBody CreateBusinessResource resource) {
        var command = BusinessResourceAssembler.toCommandFromResource(resource);
        Long businessId = commandService.handle(command);
        return queryService.handle(new GetBusinessByIdQuery(businessId))
                .map(entity -> new ResponseEntity<>(BusinessResourceAssembler.toResourceFromEntity(entity), HttpStatus.CREATED))
                .orElseThrow(() -> new BusinessNotFound(businessId));
    }

    @PutMapping(value = "/{businessId}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar información de un negocio")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Negocio actualizado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "No autorizado para gestionar este negocio"),
            @ApiResponse(responseCode = "404", description = "Negocio o Administrador no encontrado"),
            @ApiResponse(responseCode = "409", description = "Nombre comercial o razón social ya existente")
    })
    public ResponseEntity<BusinessResource> updateBusiness(
            @PathVariable Long businessId,
            @Valid @RequestBody UpdateBusinessResource resource) {
        var command = BusinessResourceAssembler.toCommandFromResource(businessId, resource);
        commandService.handle(command);
        return queryService.handle(new GetBusinessByIdQuery(businessId))
                .map(entity -> ResponseEntity.ok(BusinessResourceAssembler.toResourceFromEntity(entity)))
                .orElseThrow(() -> new BusinessNotFound(businessId));
    }

    @GetMapping("/{businessId}")
    @Operation(summary = "Obtener negocio por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Negocio encontrado"),
            @ApiResponse(responseCode = "404", description = "Negocio no encontrado")
    })
    public ResponseEntity<BusinessResource> getBusinessById(@PathVariable Long businessId) {
        return queryService.handle(new GetBusinessByIdQuery(businessId))
                .map(entity -> ResponseEntity.ok(BusinessResourceAssembler.toResourceFromEntity(entity)))
                .orElseThrow(() -> new BusinessNotFound(businessId));
    }

    @GetMapping("/by-administrator/{administratorId}")
    @Operation(summary = "Obtener negocio asignado a un administrador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Negocio encontrado"),
            @ApiResponse(responseCode = "404", description = "Negocio no encontrado para el administrador")
    })
    public ResponseEntity<BusinessResource> getBusinessByAdministratorId(@PathVariable Long administratorId) {
        return queryService.handle(new GetBusinessByAdministratorIdQuery(administratorId))
                .map(entity -> ResponseEntity.ok(BusinessResourceAssembler.toResourceFromEntity(entity)))
                .orElseThrow(BusinessNotFound::new);
    }
}
