package com.resolum.alivia.profiles.interfaces.rest.controllers;

import com.resolum.alivia.profiles.domain.exceptions.BusinessProfileNotFoundException;
import com.resolum.alivia.profiles.domain.model.queries.GetBusinessProfileByIdQuery;
import com.resolum.alivia.profiles.domain.services.BusinessProfileCommandService;
import com.resolum.alivia.profiles.domain.services.BusinessProfileQueryService;
import com.resolum.alivia.profiles.interfaces.rest.resources.BusinessProfileResource;
import com.resolum.alivia.profiles.interfaces.rest.resources.CreateBusinessProfileResource;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdateBusinessProfileResource;
import com.resolum.alivia.profiles.interfaces.rest.transform.BusinessProfileResourceFromEntityAssembler;
import com.resolum.alivia.profiles.interfaces.rest.transform.CreateBusinessProfileCommandFromResourceAssembler;
import com.resolum.alivia.profiles.interfaces.rest.transform.UpdateBusinessProfileCommandFromResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@RestController
@RequestMapping(value = "/api/v1/business-profiles", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Business Profiles", description = "Endpoints para la gestión de perfiles comerciales (empresas/centros)")
public class BusinessProfilesController {

    private final BusinessProfileCommandService commandService;
    private final BusinessProfileQueryService queryService;

    public BusinessProfilesController(BusinessProfileCommandService commandService, BusinessProfileQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping(consumes = MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear perfil comercial (Multipart/form-data con archivo de logo/foto)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Perfil comercial creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "409", description = "RUC, nombre comercial, razón social o email ya existente")
    })
    public ResponseEntity<BusinessProfileResource> createBusinessProfile(@ModelAttribute CreateBusinessProfileResource resource) {
        var command = CreateBusinessProfileCommandFromResourceAssembler.toCommandFromResource(resource);
        var profileId = commandService.handle(command);
        return queryService.handle(new GetBusinessProfileByIdQuery(profileId))
                .map(entity -> new ResponseEntity<>(BusinessProfileResourceFromEntityAssembler.toResourceFromEntity(entity), HttpStatus.CREATED))
                .orElseThrow(() -> new IllegalStateException(
                        "Business profile " + profileId + " was not found after creation."));
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "Obtener perfil comercial por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado")
    })
    public ResponseEntity<BusinessProfileResource> getBusinessProfileById(@PathVariable Long profileId) {
        var query = new GetBusinessProfileByIdQuery(profileId);
        var profile = queryService.handle(query)
                .orElseThrow(() -> new BusinessProfileNotFoundException(profileId));
        return ResponseEntity.ok(BusinessProfileResourceFromEntityAssembler.toResourceFromEntity(profile));
    }

    @PutMapping(value = "/{profileId}", consumes = MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Actualizar información comercial (Multipart/form-data con archivo de logo/foto)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos")
    })
    public ResponseEntity<BusinessProfileResource> updateBusinessProfile(
            @PathVariable Long profileId,
            @ModelAttribute UpdateBusinessProfileResource resource) {
        var command = UpdateBusinessProfileCommandFromResourceAssembler.toCommandFromResource(profileId, resource);
        var updatedId = commandService.handle(command);
        var profile = queryService.handle(new GetBusinessProfileByIdQuery(updatedId));
        return profile.map(entity -> ResponseEntity.ok(BusinessProfileResourceFromEntityAssembler.toResourceFromEntity(entity)))
                .orElseThrow(() -> new BusinessProfileNotFoundException(updatedId));
    }
}
