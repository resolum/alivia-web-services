package com.resolum.alivia.business.interfaces.rest.controllers;

import com.resolum.alivia.business.domain.exceptions.AdministratorNotFound;
import com.resolum.alivia.business.domain.model.queries.GetAdministratorByIdQuery;
import com.resolum.alivia.business.domain.model.queries.GetAdministratorByUserIdQuery;
import com.resolum.alivia.business.domain.services.AdministratorCommandService;
import com.resolum.alivia.business.domain.services.AdministratorQueryService;
import com.resolum.alivia.business.interfaces.rest.resources.AdministratorResource;
import com.resolum.alivia.business.interfaces.rest.resources.CreateAdministratorResource;
import com.resolum.alivia.business.interfaces.rest.resources.UpdateAdministratorResource;
import com.resolum.alivia.business.interfaces.rest.transform.AdministratorResourceAssembler;
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
@RequestMapping(value = "/api/v1/administrators", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Administradores", description = "Gestión de administradores de negocio")
public class AdministratorsController {

    private final AdministratorCommandService commandService;
    private final AdministratorQueryService queryService;

    public AdministratorsController(AdministratorCommandService commandService, AdministratorQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un administrador")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Administrador registrado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "409", description = "DNI, email corporativo o email de respaldo ya registrado")
    })
    public ResponseEntity<AdministratorResource> createAdministrator(@Valid @RequestBody CreateAdministratorResource resource) {
        var command = AdministratorResourceAssembler.toCommandFromResource(resource);
        Long administratorId = commandService.handle(command);
        return queryService.handle(new GetAdministratorByIdQuery(administratorId))
                .map(entity -> new ResponseEntity<>(AdministratorResourceAssembler.toResourceFromEntity(entity), HttpStatus.CREATED))
                .orElseThrow(() -> new AdministratorNotFound(administratorId));
    }

    @PutMapping(value = "/{administratorId}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar información de un administrador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Administrador actualizado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Administrador no encontrado"),
            @ApiResponse(responseCode = "409", description = "Email de respaldo ya registrado por otro administrador")
    })
    public ResponseEntity<AdministratorResource> updateAdministrator(
            @PathVariable Long administratorId,
            @Valid @RequestBody UpdateAdministratorResource resource) {
        var command = AdministratorResourceAssembler.toCommandFromResource(administratorId, resource);
        commandService.handle(command);
        return queryService.handle(new GetAdministratorByIdQuery(administratorId))
                .map(entity -> ResponseEntity.ok(AdministratorResourceAssembler.toResourceFromEntity(entity)))
                .orElseThrow(() -> new AdministratorNotFound(administratorId));
    }

    @GetMapping("/{administratorId}")
    @Operation(summary = "Obtener administrador por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Administrador encontrado"),
            @ApiResponse(responseCode = "404", description = "Administrador no encontrado")
    })
    public ResponseEntity<AdministratorResource> getAdministratorById(@PathVariable Long administratorId) {
        return queryService.handle(new GetAdministratorByIdQuery(administratorId))
                .map(entity -> ResponseEntity.ok(AdministratorResourceAssembler.toResourceFromEntity(entity)))
                .orElseThrow(() -> new AdministratorNotFound(administratorId));
    }

    @GetMapping("/by-user/{userId}")
    @Operation(summary = "Obtener administrador por ID de usuario IAM")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Administrador encontrado"),
            @ApiResponse(responseCode = "404", description = "Administrador no encontrado para el usuario")
    })
    public ResponseEntity<AdministratorResource> getAdministratorByUserId(@PathVariable Long userId) {
        return queryService.handle(new GetAdministratorByUserIdQuery(userId))
                .map(entity -> ResponseEntity.ok(AdministratorResourceAssembler.toResourceFromEntity(entity)))
                .orElseThrow(AdministratorNotFound::new);
    }
}
