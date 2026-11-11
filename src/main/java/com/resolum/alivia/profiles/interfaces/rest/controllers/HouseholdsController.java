package com.resolum.alivia.profiles.interfaces.rest.controllers;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.queries.GetActiveHouseholdByUserIdQuery;
import com.resolum.alivia.profiles.domain.model.valueobjects.PersonName;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdMembersQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdQuery;
import com.resolum.alivia.profiles.domain.services.HouseholdAccessService;
import com.resolum.alivia.profiles.domain.services.HouseholdCommandService;
import com.resolum.alivia.profiles.domain.services.HouseholdQueryService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.interfaces.rest.resources.CreateHouseholdResource;
import com.resolum.alivia.profiles.interfaces.rest.resources.HouseholdMemberResource;
import com.resolum.alivia.profiles.interfaces.rest.resources.HouseholdResource;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdateHouseholdResource;
import com.resolum.alivia.profiles.interfaces.rest.transform.CreateHouseholdCommandFromResourceAssembler;
import com.resolum.alivia.profiles.interfaces.rest.transform.HouseholdMemberResourceFromEntityAssembler;
import com.resolum.alivia.profiles.interfaces.rest.transform.HouseholdResourceFromEntityAssembler;
import com.resolum.alivia.profiles.interfaces.rest.transform.UpdateHouseholdCommandFromResourceAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = {"/api/v1/households", "/api/v1/household"}, produces = APPLICATION_JSON_VALUE)
@Tag(name = "Households", description = "Endpoints de gestión y consulta de hogares y sus miembros")
public class HouseholdsController {

    private final HouseholdQueryService queryService;
    private final HouseholdAccessService accessService;
    private final HouseholdCommandService commandService;

    /** Names the members in the members list; optional so the controller still works without it. */
    @Autowired(required = false)
    private PersonProfileRepository personProfileRepository;

    public HouseholdsController(HouseholdQueryService queryService,
                                HouseholdAccessService accessService,
                                HouseholdCommandService commandService) {
        this.queryService = queryService;
        this.accessService = accessService;
        this.commandService = commandService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un nuevo hogar")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Hogar creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos")
    })
    public ResponseEntity<HouseholdResource> createHousehold(
            @RequestBody CreateHouseholdResource resource,
            Principal principal) {
        Long responsibleUserId = null;
        if (principal != null) {
            try {
                responsibleUserId = userIdOf(principal);
            } catch (Exception ignored) {
                // Ignore when principal format differs
            }
        }
        if (responsibleUserId == null && resource.responsibleAccountId() != null) {
            try {
                responsibleUserId = Long.valueOf(resource.responsibleAccountId());
            } catch (NumberFormatException ignored) {
                // If it's a UUID string, responsibleUserId is resolved from principal or stays null
            }
        }
        var command = CreateHouseholdCommandFromResourceAssembler.toCommandFromResource(resource, responsibleUserId);
        var householdId = commandService.handle(command);
        var household = queryService.handle(new GetHouseholdQuery(householdId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(HouseholdResourceFromEntityAssembler.toResourceFromEntity(household));
    }

    @PutMapping("/{householdId}")
    @Operation(summary = "Actualizar información general de un hogar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hogar actualizado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "El usuario no es miembro activo o no tiene permisos para gestionar el hogar"),
            @ApiResponse(responseCode = "404", description = "Hogar no encontrado")
    })
    public ResponseEntity<HouseholdResource> updateHousehold(
            @PathVariable Long householdId,
            @RequestBody UpdateHouseholdResource resource,
            Principal principal) {
        if (principal != null) {
            var userId = userIdOf(principal);
            accessService.assertCanManage(householdId, userId);
        }
        var command = UpdateHouseholdCommandFromResourceAssembler.toCommandFromResource(householdId, resource);
        var updatedId = commandService.handle(command);
        var household = queryService.handle(new GetHouseholdQuery(updatedId));
        return ResponseEntity.ok(HouseholdResourceFromEntityAssembler.toResourceFromEntity(household));
    }

    @GetMapping("/mine")
    @Operation(summary = "Consultar el hogar activo del usuario autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hogar encontrado"),
            @ApiResponse(responseCode = "404", description = "Hogar no encontrado para el usuario actual")
    })
    public ResponseEntity<HouseholdResource> getMyHousehold(Principal principal) {
        var userId = userIdOf(principal);
        return queryService.handle(new GetActiveHouseholdByUserIdQuery(new UserId(userId)))
                .map(household -> ResponseEntity.ok(HouseholdResourceFromEntityAssembler.toResourceFromEntity(household)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{householdId}")
    @Operation(summary = "Consultar un hogar y sus miembros")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hogar encontrado"),
            @ApiResponse(responseCode = "403", description = "El usuario no es miembro activo del hogar"),
            @ApiResponse(responseCode = "404", description = "Hogar no encontrado")
    })
    public HouseholdResource getHousehold(@PathVariable Long householdId, Principal principal) {
        var userId = userIdOf(principal);
        accessService.assertCanRead(householdId, userId);
        var household = queryService.handle(new GetHouseholdQuery(householdId));
        return HouseholdResourceFromEntityAssembler.toResourceFromEntity(household);
    }

    @GetMapping("/{householdId}/members")
    @Operation(summary = "Consultar los miembros de un hogar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Miembros encontrados"),
            @ApiResponse(responseCode = "403", description = "El usuario no es miembro activo del hogar"),
            @ApiResponse(responseCode = "404", description = "Hogar no encontrado")
    })
    public List<HouseholdMemberResource> getHouseholdMembers(@PathVariable Long householdId,
                                                             Principal principal) {
        var userId = userIdOf(principal);
        accessService.assertCanRead(householdId, userId);
        return queryService.handle(new GetHouseholdMembersQuery(householdId))
                .stream()
                .map(member -> HouseholdMemberResourceFromEntityAssembler.toResourceFromEntity(member,
                        fullNameOf(member.getUserId().userId())))
                .toList();
    }

    /** "Lucia Salazar" for a member with a person profile; null otherwise. */
    private String fullNameOf(Long userId) {
        if (personProfileRepository == null || userId == null) return null;
        return personProfileRepository.findByUserId(userId)
                .map(PersonProfile::getPersonName)
                .map(PersonName::getFullName)
                .orElse(null);
    }

    private static Long userIdOf(Principal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Authenticated user identifier is invalid.", exception);
        }
    }
}
