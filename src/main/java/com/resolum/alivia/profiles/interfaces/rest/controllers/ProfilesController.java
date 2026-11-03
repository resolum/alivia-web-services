package com.resolum.alivia.profiles.interfaces.rest.controllers;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.resolum.alivia.profiles.domain.exceptions.DniNotEditableException;
import com.resolum.alivia.profiles.domain.exceptions.EmailNotEditableException;
import com.resolum.alivia.profiles.domain.exceptions.OwnProfileNotFoundException;
import com.resolum.alivia.profiles.domain.model.queries.GetOwnProfileQuery;
import com.resolum.alivia.profiles.domain.services.OwnProfileCommandService;
import com.resolum.alivia.profiles.domain.services.OwnProfileQueryService;
import com.resolum.alivia.profiles.interfaces.rest.resources.OwnProfileResource;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdateOwnProfileResource;
import com.resolum.alivia.profiles.interfaces.rest.transform.OwnProfileResourceFromEntityAssembler;
import com.resolum.alivia.profiles.interfaces.rest.transform.UpdateOwnProfileCommandFromResourceAssembler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@RestController
@RequestMapping(value = "/api/v1/profiles", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Profiles", description = "Endpoints del perfil propio del usuario autenticado")
public class ProfilesController {

    private final OwnProfileQueryService queryService;
    private final OwnProfileCommandService commandService;

    public ProfilesController(OwnProfileQueryService queryService,
                              OwnProfileCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar el perfil propio")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
            @ApiResponse(responseCode = "404", description = "El usuario no tiene un perfil vinculado")
    })
    public ResponseEntity<OwnProfileResource> getOwnProfile(Principal principal) {
        var userId = userIdOf(principal);
        var profile = queryService.handle(new GetOwnProfileQuery(userId))
                .orElseThrow(() -> new OwnProfileNotFoundException(userId));
        return ResponseEntity.ok(OwnProfileResourceFromEntityAssembler.toResourceFromEntity(profile));
    }

    @PatchMapping(value = "/me", consumes = MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Actualizar el perfil propio (Multipart/form-data con archivo de foto opcional)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos o campo no editable"),
            @ApiResponse(responseCode = "404", description = "El usuario no tiene un perfil vinculado")
    })
    public ResponseEntity<OwnProfileResource> updateOwnProfile(
            @ModelAttribute UpdateOwnProfileResource resource,
            @RequestParam Map<String, String> parameters,
            Principal principal) {
        rejectReadOnlyFields(parameters);
        var userId = userIdOf(principal);
        var command = UpdateOwnProfileCommandFromResourceAssembler.toCommandFromResource(userId, resource);
        commandService.handle(command);
        var profile = queryService.handle(new GetOwnProfileQuery(userId))
                .orElseThrow(() -> new OwnProfileNotFoundException(userId));
        return ResponseEntity.ok(OwnProfileResourceFromEntityAssembler.toResourceFromEntity(profile));
    }

    private static void rejectReadOnlyFields(Map<String, String> parameters) {
        for (String name : parameters.keySet()) {
            if ("email".equalsIgnoreCase(name) || "emailaddress".equalsIgnoreCase(name)
                    || "email_address".equalsIgnoreCase(name)) {
                throw new EmailNotEditableException();
            }
            if ("dni".equalsIgnoreCase(name)) {
                throw new DniNotEditableException();
            }
        }
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
