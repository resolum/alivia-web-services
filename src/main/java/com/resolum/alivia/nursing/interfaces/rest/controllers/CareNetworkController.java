package com.resolum.alivia.nursing.interfaces.rest.controllers;

import com.resolum.alivia.nursing.domain.model.queries.GetAssistedPersonsQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetCaregiversQuery;
import com.resolum.alivia.nursing.domain.services.AssistedPersonCommandService;
import com.resolum.alivia.nursing.domain.services.CareNetworkQueryService;
import com.resolum.alivia.nursing.domain.services.CaregiverMembershipCommandService;
import com.resolum.alivia.nursing.interfaces.rest.resources.AssistedPersonResource;
import com.resolum.alivia.nursing.interfaces.rest.resources.CaregiverResource;
import com.resolum.alivia.nursing.interfaces.rest.resources.LinkCaregiverResource;
import com.resolum.alivia.nursing.interfaces.rest.resources.RegisterAssistedPersonResource;
import com.resolum.alivia.nursing.interfaces.rest.transform.AssistedPersonResourceFromEntityAssembler;
import com.resolum.alivia.nursing.interfaces.rest.transform.CaregiverResourceFromEntityAssembler;
import com.resolum.alivia.nursing.interfaces.rest.transform.LinkCaregiverCommandFromResourceAssembler;
import com.resolum.alivia.nursing.interfaces.rest.transform.RegisterAssistedPersonCommandFromResourceAssembler;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * REST controller for the care network of a household: its assisted persons and caregivers.
 *
 * <p>The user always comes from the authenticated principal, whose name is the IAM user id.
 * Reading needs an active household membership; registering and linking need management access.</p>
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/households/{householdId}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Care Network", description = "Personas asistidas y cuidadores de un hogar")
public class CareNetworkController {

    private final CareNetworkQueryService careNetworkQueryService;
    private final AssistedPersonCommandService assistedPersonCommandService;
    private final CaregiverMembershipCommandService caregiverMembershipCommandService;

    /**
     * Creates the controller.
     *
     * @param careNetworkQueryService           the query service
     * @param assistedPersonCommandService      the assisted person command service
     * @param caregiverMembershipCommandService the caregiver membership command service
     */
    public CareNetworkController(CareNetworkQueryService careNetworkQueryService,
                                 AssistedPersonCommandService assistedPersonCommandService,
                                 CaregiverMembershipCommandService caregiverMembershipCommandService) {
        this.careNetworkQueryService = careNetworkQueryService;
        this.assistedPersonCommandService = assistedPersonCommandService;
        this.caregiverMembershipCommandService = caregiverMembershipCommandService;
    }

    /**
     * Registers an assisted person in the household.
     *
     * @param principal   the authenticated user
     * @param householdId the household identifier
     * @param resource    the assisted person data
     * @return the registered assisted person
     */
    @PostMapping(value = "/assisted-persons", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar una persona asistida en el hogar",
            description = "Crea la cuenta de la persona asistida, la añade al hogar con acceso de solo lectura y "
                    + "vincula al usuario autenticado como su familiar. Requiere gestionar el hogar.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Persona asistida registrada",
                    content = @Content(schema = @Schema(implementation = AssistedPersonResource.class))),
            @ApiResponse(responseCode = "400", description = "Cuerpo inválido",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Sin usuario autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no puede gestionar el hogar",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Hogar no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "El correo o el DNI ya están registrados",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<AssistedPersonResource> registerAssistedPerson(
            @Parameter(hidden = true) Principal principal,
            @Parameter(description = "Identificador del hogar", example = "1") @PathVariable Long householdId,
            @Valid @RequestBody RegisterAssistedPersonResource resource) {
        log.info("Received register assisted person request for household {}", householdId);
        var command = RegisterAssistedPersonCommandFromResourceAssembler.toCommandFromResource(
                userIdOf(principal), householdId, resource);
        var assistedPerson = assistedPersonCommandService.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AssistedPersonResourceFromEntityAssembler.toResourceFromEntity(assistedPerson));
    }

    /**
     * Lists the assisted persons of the household.
     *
     * @param principal   the authenticated user
     * @param householdId the household identifier
     * @return the assisted persons
     */
    @GetMapping("/assisted-persons")
    @Operation(summary = "Listar las personas asistidas del hogar",
            description = "Disponible para cualquier miembro activo del hogar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Personas asistidas del hogar",
                    content = @Content(schema = @Schema(implementation = AssistedPersonResource.class))),
            @ApiResponse(responseCode = "401", description = "Sin usuario autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no es miembro activo del hogar",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Hogar no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<AssistedPersonResource> getAssistedPersons(
            @Parameter(hidden = true) Principal principal,
            @Parameter(description = "Identificador del hogar", example = "1") @PathVariable Long householdId) {
        log.info("Received assisted persons request for household {}", householdId);
        return careNetworkQueryService.handle(new GetAssistedPersonsQuery(userIdOf(principal), householdId)).stream()
                .map(AssistedPersonResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
    }

    /**
     * Links a caregiver to an assisted person of the household.
     *
     * @param principal   the authenticated user
     * @param householdId the household identifier
     * @param resource    the link data
     * @return the linked caregiver
     */
    @PostMapping(value = "/caregivers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Vincular un cuidador a una persona asistida del hogar",
            description = "Vincula a un usuario existente como cuidador y lo añade al hogar con acceso de lectura "
                    + "si aún no es miembro. Requiere gestionar el hogar.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuidador vinculado",
                    content = @Content(schema = @Schema(implementation = CaregiverResource.class))),
            @ApiResponse(responseCode = "400", description = "Cuerpo inválido o el cuidador es la propia persona asistida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Sin usuario autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no puede gestionar el hogar",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Hogar, persona asistida o cuidador no encontrados",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "El cuidador ya está vinculado a la persona asistida "
                    + "o tiene una membresía revocada en el hogar",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<CaregiverResource> linkCaregiver(
            @Parameter(hidden = true) Principal principal,
            @Parameter(description = "Identificador del hogar", example = "1") @PathVariable Long householdId,
            @Valid @RequestBody LinkCaregiverResource resource) {
        log.info("Received link caregiver request for household {}", householdId);
        var command = LinkCaregiverCommandFromResourceAssembler.toCommandFromResource(
                userIdOf(principal), householdId, resource);
        var caregiver = caregiverMembershipCommandService.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CaregiverResourceFromEntityAssembler.toResourceFromEntity(caregiver));
    }

    /**
     * Lists the caregivers of the household.
     *
     * @param principal   the authenticated user
     * @param householdId the household identifier
     * @return the caregivers
     */
    @GetMapping("/caregivers")
    @Operation(summary = "Listar los cuidadores del hogar",
            description = "Cuidadores vinculados a las personas asistidas del hogar. Disponible para cualquier "
                    + "miembro activo del hogar; el correo electrónico solo se informa a quien gestiona el hogar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuidadores del hogar",
                    content = @Content(schema = @Schema(implementation = CaregiverResource.class))),
            @ApiResponse(responseCode = "401", description = "Sin usuario autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no es miembro activo del hogar",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Hogar no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<CaregiverResource> getCaregivers(
            @Parameter(hidden = true) Principal principal,
            @Parameter(description = "Identificador del hogar", example = "1") @PathVariable Long householdId) {
        log.info("Received caregivers request for household {}", householdId);
        return careNetworkQueryService.handle(new GetCaregiversQuery(userIdOf(principal), householdId)).stream()
                .map(CaregiverResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
    }

    private static UserId userIdOf(Principal principal) {
        return new UserId(Long.valueOf(principal.getName()));
    }
}
