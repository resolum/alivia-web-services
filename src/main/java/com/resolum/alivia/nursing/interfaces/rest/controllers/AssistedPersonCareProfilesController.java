package com.resolum.alivia.nursing.interfaces.rest.controllers;

import com.resolum.alivia.nursing.domain.model.queries.GetAssistedPersonCareProfileQuery;
import com.resolum.alivia.nursing.domain.services.AssistedPersonCareProfileCommandService;
import com.resolum.alivia.nursing.domain.services.AssistedPersonCareProfileQueryService;
import com.resolum.alivia.nursing.interfaces.rest.resources.AssistedPersonCareProfileResource;
import com.resolum.alivia.nursing.interfaces.rest.resources.SaveAssistedPersonCareProfileResource;
import com.resolum.alivia.nursing.interfaces.rest.transform.AssistedPersonCareProfileResourceFromEntityAssembler;
import com.resolum.alivia.nursing.interfaces.rest.transform.SaveAssistedPersonCareProfileCommandFromResourceAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * REST controller for the care profile of the assisted person, filled in while contracting.
 *
 * <p>The profile belongs to the authenticated responsible client (the principal name is the IAM
 * user id), so it can be saved before the household exists. It creates no account for the
 * assisted person; registering them in a household is done by {@link CareNetworkController}.</p>
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/assisted-person-care-profiles", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Assisted Person Care Profile", description = "Perfil de la persona asistida registrado al contratar")
public class AssistedPersonCareProfilesController {

    private final AssistedPersonCareProfileCommandService commandService;
    private final AssistedPersonCareProfileQueryService queryService;

    /**
     * Creates the controller.
     *
     * @param commandService the command service
     * @param queryService   the query service
     */
    public AssistedPersonCareProfilesController(AssistedPersonCareProfileCommandService commandService,
                                                AssistedPersonCareProfileQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /**
     * Creates or replaces the care profile of the authenticated client.
     *
     * @param principal the authenticated client
     * @param resource  the profile data
     * @return the saved profile
     */
    @PutMapping(value = "/mine", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Guardar el perfil de la persona asistida",
            description = "Crea o reemplaza el perfil de la persona asistida del cliente autenticado. "
                    + "No crea una cuenta para la persona asistida.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil guardado",
                    content = @Content(schema = @Schema(implementation = AssistedPersonCareProfileResource.class))),
            @ApiResponse(responseCode = "400", description = "Cuerpo inválido",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Sin usuario autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public AssistedPersonCareProfileResource saveMine(
            @Parameter(hidden = true) Principal principal,
            @Valid @RequestBody SaveAssistedPersonCareProfileResource resource) {
        log.info("Received save assisted person care profile request");
        var command = SaveAssistedPersonCareProfileCommandFromResourceAssembler
                .toCommandFromResource(userIdOf(principal), resource);
        return AssistedPersonCareProfileResourceFromEntityAssembler.toResourceFromEntity(commandService.handle(command));
    }

    /**
     * Returns the care profile of the authenticated client.
     *
     * @param principal the authenticated client
     * @return the profile, or 404 when there is none yet
     */
    @GetMapping("/mine")
    @Operation(summary = "Obtener el perfil de la persona asistida",
            description = "Devuelve el perfil de la persona asistida que registró el cliente autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil encontrado",
                    content = @Content(schema = @Schema(implementation = AssistedPersonCareProfileResource.class))),
            @ApiResponse(responseCode = "401", description = "Sin usuario autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "El cliente aún no registró a la persona asistida")
    })
    public ResponseEntity<AssistedPersonCareProfileResource> getMine(@Parameter(hidden = true) Principal principal) {
        return queryService.handle(new GetAssistedPersonCareProfileQuery(userIdOf(principal)))
                .map(AssistedPersonCareProfileResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static UserId userIdOf(Principal principal) {
        return new UserId(Long.valueOf(principal.getName()));
    }
}
