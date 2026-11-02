package com.resolum.alivia.profiles.interfaces.rest.controllers;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdMembersQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdQuery;
import com.resolum.alivia.profiles.domain.services.HouseholdQueryService;
import com.resolum.alivia.profiles.interfaces.rest.resources.HouseholdMemberResource;
import com.resolum.alivia.profiles.interfaces.rest.resources.HouseholdResource;
import com.resolum.alivia.profiles.interfaces.rest.transform.HouseholdMemberResourceFromEntityAssembler;
import com.resolum.alivia.profiles.interfaces.rest.transform.HouseholdResourceFromEntityAssembler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/households", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Households", description = "Endpoints de consulta de hogares y sus miembros")
public class HouseholdsController {

    private final HouseholdQueryService queryService;

    public HouseholdsController(HouseholdQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{householdId}")
    @Operation(summary = "Consultar un hogar y sus miembros")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hogar encontrado"),
            @ApiResponse(responseCode = "403", description = "El usuario no es miembro activo del hogar"),
            @ApiResponse(responseCode = "404", description = "Hogar no encontrado")
    })
    public HouseholdResource getHousehold(@PathVariable Long householdId, Principal principal) {
        var household = queryService.handle(new GetHouseholdQuery(householdId, userIdOf(principal)));
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
        return queryService.handle(new GetHouseholdMembersQuery(householdId, userIdOf(principal)))
                .stream()
                .map(HouseholdMemberResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
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
