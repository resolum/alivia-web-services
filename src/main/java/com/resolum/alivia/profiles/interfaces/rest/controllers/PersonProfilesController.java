package com.resolum.alivia.profiles.interfaces.rest.controllers;

import com.resolum.alivia.profiles.domain.exceptions.PersonProfileNotFoundException;
import com.resolum.alivia.profiles.domain.model.queries.GetPersonProfileByIdQuery;
import com.resolum.alivia.profiles.domain.services.PersonProfileCommandService;
import com.resolum.alivia.profiles.domain.services.PersonProfileQueryService;
import com.resolum.alivia.profiles.interfaces.rest.resources.CreatePersonProfileResource;
import com.resolum.alivia.profiles.interfaces.rest.resources.PersonProfileResource;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdatePersonProfileResource;
import com.resolum.alivia.profiles.interfaces.rest.transform.CreatePersonProfileCommandFromResourceAssembler;
import com.resolum.alivia.profiles.interfaces.rest.transform.PersonProfileResourceFromEntityAssembler;
import com.resolum.alivia.profiles.interfaces.rest.transform.UpdatePersonProfileCommandFromResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/person-profiles", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Person Profiles", description = "Endpoints para la gestión de perfiles de personas")
public class PersonProfilesController {

    private final PersonProfileCommandService commandService;
    private final PersonProfileQueryService queryService;

    public PersonProfilesController(PersonProfileCommandService commandService, PersonProfileQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear perfil de persona")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Perfil creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "409", description = "Email o DNI ya existente")
    })
    public ResponseEntity<PersonProfileResource> createPersonProfile(@RequestBody CreatePersonProfileResource resource) {
        var command = CreatePersonProfileCommandFromResourceAssembler.toCommandFromResource(resource);
        var profileId = commandService.handle(command);
        var profile = queryService.handle(new GetPersonProfileByIdQuery(profileId));
        return profile.map(entity -> new ResponseEntity<>(PersonProfileResourceFromEntityAssembler.toResourceFromEntity(entity), HttpStatus.CREATED))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "Obtener perfil de persona por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado")
    })
    public ResponseEntity<PersonProfileResource> getPersonProfileById(@PathVariable Long profileId) {
        var query = new GetPersonProfileByIdQuery(profileId);
        var profile = queryService.handle(query)
                .orElseThrow(() -> new PersonProfileNotFoundException(profileId));
        return ResponseEntity.ok(PersonProfileResourceFromEntityAssembler.toResourceFromEntity(profile));
    }

    @PutMapping("/{profileId}")
    @Operation(summary = "Actualizar información personal")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos")
    })
    public ResponseEntity<PersonProfileResource> updatePersonProfile(
            @PathVariable Long profileId,
            @RequestBody UpdatePersonProfileResource resource) {
        var command = UpdatePersonProfileCommandFromResourceAssembler.toCommandFromResource(profileId, resource);
        var updatedId = commandService.handle(command);
        var profile = queryService.handle(new GetPersonProfileByIdQuery(updatedId));
        return profile.map(entity -> ResponseEntity.ok(PersonProfileResourceFromEntityAssembler.toResourceFromEntity(entity)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
