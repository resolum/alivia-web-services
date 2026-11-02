package com.resolum.alivia.nursing.interfaces.rest.controllers;

import com.resolum.alivia.nursing.domain.model.queries.GetMedicationAdministrationQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationId;
import com.resolum.alivia.nursing.domain.services.MedicationAdministrationCommandService;
import com.resolum.alivia.nursing.domain.services.MedicationAdministrationQueryService;
import com.resolum.alivia.nursing.interfaces.rest.resources.MedicationAdministrationResource;
import com.resolum.alivia.nursing.interfaces.rest.resources.RecordMedicationOutcomeResource;
import com.resolum.alivia.nursing.interfaces.rest.transform.MedicationAdministrationResourceFromEntityAssembler;
import com.resolum.alivia.nursing.interfaces.rest.transform.RecordMedicationOutcomeCommandFromResourceAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * REST controller for medication administrations and their outcomes.
 *
 * <p>The user always comes from the authenticated principal, whose name is the IAM user id.</p>
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/medication-administrations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Medication Administrations", description = "Scheduled medication administrations and their recorded outcomes")
public class MedicationAdministrationsController {

    private final MedicationAdministrationQueryService medicationAdministrationQueryService;
    private final MedicationAdministrationCommandService medicationAdministrationCommandService;

    /**
     * Creates the controller.
     *
     * @param medicationAdministrationQueryService   the query service
     * @param medicationAdministrationCommandService the command service
     */
    public MedicationAdministrationsController(
            MedicationAdministrationQueryService medicationAdministrationQueryService,
            MedicationAdministrationCommandService medicationAdministrationCommandService) {
        this.medicationAdministrationQueryService = medicationAdministrationQueryService;
        this.medicationAdministrationCommandService = medicationAdministrationCommandService;
    }

    /**
     * Returns one scheduled medication administration.
     *
     * @param principal        the authenticated user
     * @param administrationId the administration identifier
     * @return the administration
     */
    @GetMapping("/{administrationId}")
    @Operation(summary = "Get a medication administration",
            description = "Returns the medication, assisted person, schedule, responsible person, status and, "
                    + "when recorded, the outcome. Available to the responsible caregiver with an active link "
                    + "and to the relatives of the assisted person.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medication administration",
                    content = @Content(schema = @Schema(implementation = MedicationAdministrationResource.class))),
            @ApiResponse(responseCode = "400", description = "Malformed administration identifier",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No authenticated user",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not the responsible caregiver nor a relative",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Administration not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public MedicationAdministrationResource getMedicationAdministration(
            @Parameter(hidden = true) Principal principal,
            @Parameter(description = "Administration identifier, {assignmentId}-{yyyyMMdd}", example = "12-20261026")
            @PathVariable String administrationId) {
        log.info("Received medication administration request for {}", administrationId);
        var query = new GetMedicationAdministrationQuery(userIdOf(principal),
                MedicationAdministrationId.parse(administrationId));
        return MedicationAdministrationResourceFromEntityAssembler.toResourceFromEntity(
                medicationAdministrationQueryService.handle(query));
    }

    /**
     * Records the outcome of a medication administration.
     *
     * @param principal        the authenticated user
     * @param administrationId the administration identifier
     * @param resource         the outcome
     * @return the administration with its outcome
     */
    @PostMapping(value = "/{administrationId}/outcomes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Record a medication outcome",
            description = "Records whether the medication was administered or omitted. One outcome per "
                    + "administration; it cannot be changed. A moment after the scheduled end is accepted "
                    + "and marked as late.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Outcome recorded",
                    content = @Content(schema = @Schema(implementation = MedicationAdministrationResource.class))),
            @ApiResponse(responseCode = "400", description = "Malformed identifier or body, omission without reason, "
                    + "moment in the future or before the scheduled start",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No authenticated user",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not the responsible caregiver nor a relative",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Administration not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "The administration already has an outcome",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<MedicationAdministrationResource> recordMedicationOutcome(
            @Parameter(hidden = true) Principal principal,
            @Parameter(description = "Administration identifier, {assignmentId}-{yyyyMMdd}", example = "12-20261026")
            @PathVariable String administrationId,
            @RequestBody RecordMedicationOutcomeResource resource) {
        log.info("Received medication outcome request for administration {}", administrationId);
        var command = RecordMedicationOutcomeCommandFromResourceAssembler.toCommandFromResource(
                userIdOf(principal), MedicationAdministrationId.parse(administrationId), resource);
        var administration = medicationAdministrationCommandService.handle(command);
        log.info("Medication outcome request completed for administration {}", administrationId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MedicationAdministrationResourceFromEntityAssembler.toResourceFromEntity(administration));
    }

    private static UserId userIdOf(Principal principal) {
        return new UserId(Long.valueOf(principal.getName()));
    }
}
