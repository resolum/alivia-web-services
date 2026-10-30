package com.resolum.alivia.profiles.interfaces.rest.controllers;

import com.resolum.alivia.profiles.domain.exceptions.StaffProfileNotFoundException;
import com.resolum.alivia.profiles.domain.model.queries.GetStaffProfileByIdQuery;
import com.resolum.alivia.profiles.domain.services.StaffProfileCommandService;
import com.resolum.alivia.profiles.domain.services.StaffProfileQueryService;
import com.resolum.alivia.profiles.interfaces.rest.resources.CreateStaffProfileResource;
import com.resolum.alivia.profiles.interfaces.rest.resources.StaffProfileResource;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdateStaffProfileResource;
import com.resolum.alivia.profiles.interfaces.rest.transform.CreateStaffProfileCommandFromResourceAssembler;
import com.resolum.alivia.profiles.interfaces.rest.transform.StaffProfileResourceFromEntityAssembler;
import com.resolum.alivia.profiles.interfaces.rest.transform.UpdateStaffProfileCommandFromResourceAssembler;
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
@RequestMapping(value = "/api/v1/staff-profiles", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Staff Profiles", description = "Endpoints para la gestión de perfiles de personal (staff)")
public class StaffProfilesController {

    private final StaffProfileCommandService commandService;
    private final StaffProfileQueryService queryService;

    public StaffProfilesController(StaffProfileCommandService commandService, StaffProfileQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping(consumes = MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear perfil de staff (Multipart/form-data con archivo de foto)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Perfil creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "409", description = "Email o DNI ya existente")
    })
    public ResponseEntity<StaffProfileResource> createStaffProfile(@ModelAttribute CreateStaffProfileResource resource) {
        var command = CreateStaffProfileCommandFromResourceAssembler.toCommandFromResource(resource);
        var profileId = commandService.handle(command);
        return queryService.handle(new GetStaffProfileByIdQuery(profileId))
                .map(entity -> new ResponseEntity<>(StaffProfileResourceFromEntityAssembler.toResourceFromEntity(entity), HttpStatus.CREATED))
                .orElseThrow(() -> new IllegalStateException(
                        "Staff profile " + profileId + " was not found after creation."));
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "Obtener perfil de staff por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado")
    })
    public ResponseEntity<StaffProfileResource> getStaffProfileById(@PathVariable Long profileId) {
        var query = new GetStaffProfileByIdQuery(profileId);
        var profile = queryService.handle(query)
                .orElseThrow(() -> new StaffProfileNotFoundException(profileId));
        return ResponseEntity.ok(StaffProfileResourceFromEntityAssembler.toResourceFromEntity(profile));
    }

    @PutMapping(value = "/{profileId}", consumes = MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Actualizar información de personal (Multipart/form-data con archivo de foto)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos")
    })
    public ResponseEntity<StaffProfileResource> updateStaffProfile(
            @PathVariable Long profileId,
            @ModelAttribute UpdateStaffProfileResource resource) {
        var command = UpdateStaffProfileCommandFromResourceAssembler.toCommandFromResource(profileId, resource);
        var updatedId = commandService.handle(command);
        var profile = queryService.handle(new GetStaffProfileByIdQuery(updatedId));
        return profile.map(entity -> ResponseEntity.ok(StaffProfileResourceFromEntityAssembler.toResourceFromEntity(entity)))
                .orElseThrow(() -> new StaffProfileNotFoundException(updatedId));
    }
}
