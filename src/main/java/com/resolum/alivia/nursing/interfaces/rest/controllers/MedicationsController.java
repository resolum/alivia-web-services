package com.resolum.alivia.nursing.interfaces.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.commands.ChangeMedicationResponsibleCommand;
import com.resolum.alivia.nursing.domain.model.commands.SuspendMedicationScheduleCommand;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationByIdQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationsByAssistedPersonQuery;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.nursing.domain.services.MedicationScheduleCommandService;
import com.resolum.alivia.nursing.domain.services.MedicationScheduleQueryService;
import com.resolum.alivia.nursing.interfaces.rest.resources.ChangeMedicationResponsibleResource;
import com.resolum.alivia.nursing.interfaces.rest.resources.CreateMedicationScheduleResource;
import com.resolum.alivia.nursing.interfaces.rest.resources.MedicationScheduleResource;
import com.resolum.alivia.nursing.interfaces.rest.resources.SuspendMedicationResource;
import com.resolum.alivia.nursing.interfaces.rest.resources.UpdateMedicationScheduleResource;
import com.resolum.alivia.nursing.interfaces.rest.transform.CreateMedicationScheduleCommandFromResourceAssembler;
import com.resolum.alivia.nursing.interfaces.rest.transform.MedicationScheduleResourceFromEntityAssembler;
import com.resolum.alivia.nursing.interfaces.rest.transform.UpdateMedicationScheduleCommandFromResourceAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for medication schedules (CareActivity type MEDICATION).
 */
@Slf4j
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Medication Schedules", description = "Programación y gestión de medicamentos indicados")
public class MedicationsController {

    private final MedicationScheduleCommandService commandService;
    private final MedicationScheduleQueryService queryService;
    private final CareMembershipRepository careMembershipRepository;
    private final ExternalProfilesService externalProfilesService;
    private final ObjectMapper objectMapper;

    public MedicationsController(
            MedicationScheduleCommandService commandService,
            MedicationScheduleQueryService queryService,
            CareMembershipRepository careMembershipRepository,
            ExternalProfilesService externalProfilesService,
            ObjectMapper objectMapper) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.careMembershipRepository = careMembershipRepository;
        this.externalProfilesService = externalProfilesService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = {
            "/api/v1/assisted-persons/{assistedPersonId}/medications",
            "/api/v1/medications"
    }, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Programar un nuevo medicamento", description = "Crea la actividad de cuidado de medicación y sus horarios correspondientes.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Medicamento programado exitosamente",
                    content = @Content(schema = @Schema(implementation = MedicationScheduleResource.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Acceso denegado a la persona asistida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<MedicationScheduleResource> scheduleMedication(
            @Parameter(hidden = true) Principal principal,
            @Parameter(description = "Identificador de la persona asistida")
            @PathVariable(required = false) Long assistedPersonId,
            @Valid @RequestBody CreateMedicationScheduleResource resource) {
        log.info("Received schedule medication request for assisted person {}", assistedPersonId);
        var command = CreateMedicationScheduleCommandFromResourceAssembler.toCommandFromResource(
                userIdOf(principal), assistedPersonId, resource);
        var activity = commandService.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResource(activity));
    }

    @GetMapping({
            "/api/v1/assisted-persons/{assistedPersonId}/medications",
            "/api/v1/medications"
    })
    @Operation(summary = "Listar los medicamentos programados", description = "Lista los medicamentos de la persona asistida.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de medicamentos",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MedicationScheduleResource.class)))),
            @ApiResponse(responseCode = "403", description = "Acceso denegado a la persona asistida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<MedicationScheduleResource> getMedications(
            @Parameter(hidden = true) Principal principal,
            @Parameter(description = "Identificador de la persona asistida en la ruta")
            @PathVariable(required = false) Long assistedPersonId,
            @Parameter(description = "Identificador de la persona asistida como query parameter opcional")
            @RequestParam(required = false) Long assistedPerson) {
        Long targetPersonId = assistedPersonId != null ? assistedPersonId : assistedPerson;
        var userId = userIdOf(principal);

        List<CareActivity> activities;
        if (targetPersonId != null) {
            activities = queryService.handle(new GetMedicationsByAssistedPersonQuery(
                    userId, new PersonProfileId(targetPersonId)));
        } else {
            // Find all assisted persons the user has active membership with
            var memberships = careMembershipRepository.findActiveByUserId(userId);
            var personIds = memberships.stream().map(CareMembership::getAssistedPersonProfileId).distinct().toList();
            activities = new ArrayList<>();
            for (PersonProfileId personId : personIds) {
                activities.addAll(queryService.handle(new GetMedicationsByAssistedPersonQuery(userId, personId)));
            }
        }

        Map<Long, String> caregiverNames = resolveCaregiverNames(activities);
        return activities.stream()
                .map(a -> {
                    Long caregiverId = a.getActivityAssignments().getActivityAssignments().isEmpty()
                            ? null : a.getActivityAssignments().getActivityAssignments().get(0).getCaregiverId();
                    String name = caregiverId != null ? caregiverNames.getOrDefault(caregiverId, "") : "";
                    return MedicationScheduleResourceFromEntityAssembler.toResourceFromEntity(a, name, objectMapper);
                })
                .toList();
    }

    @GetMapping({
            "/api/v1/medications/{medicationId}",
            "/api/v1/assisted-persons/{assistedPersonId}/medications/{medicationId}"
    })
    @Operation(summary = "Obtener el detalle de un medicamento", description = "Obtiene los datos de un medicamento programado por su ID.")
    public MedicationScheduleResource getMedicationById(
            @Parameter(hidden = true) Principal principal,
            @PathVariable Long medicationId) {
        var activity = queryService.handle(new GetMedicationByIdQuery(userIdOf(principal), medicationId));
        return toResource(activity);
    }

    @PutMapping(value = {
            "/api/v1/medications/{medicationId}",
            "/api/v1/assisted-persons/{assistedPersonId}/medications/{medicationId}"
    }, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar la indicación o programación de un medicamento")
    public MedicationScheduleResource updateMedication(
            @Parameter(hidden = true) Principal principal,
            @PathVariable Long medicationId,
            @Valid @RequestBody UpdateMedicationScheduleResource resource) {
        var command = UpdateMedicationScheduleCommandFromResourceAssembler.toCommandFromResource(
                userIdOf(principal), medicationId, resource);
        var activity = commandService.handle(command);
        return toResource(activity);
    }

    @PostMapping(value = {
            "/api/v1/medications/{medicationId}/suspend",
            "/api/v1/assisted-persons/{assistedPersonId}/medications/{medicationId}/suspend"
    }, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Suspender un medicamento programado")
    public MedicationScheduleResource suspendMedication(
            @Parameter(hidden = true) Principal principal,
            @PathVariable Long medicationId,
            @RequestBody SuspendMedicationResource resource) {
        LocalDate fromDate = resource.from() != null && !resource.from().isBlank()
                ? LocalDate.parse(resource.from())
                : LocalDate.now();
        String source = resource.source() != null && !resource.source().isBlank()
                ? resource.source()
                : "Médico tratante";

        var command = new SuspendMedicationScheduleCommand(userIdOf(principal), medicationId, fromDate, source);
        var activity = commandService.handle(command);
        return toResource(activity);
    }

    @RequestMapping(value = {
            "/api/v1/medications/{medicationId}/responsible",
            "/api/v1/assisted-persons/{assistedPersonId}/medications/{medicationId}/responsible"
    }, method = {RequestMethod.PATCH, RequestMethod.PUT}, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reasignar el cuidador responsable del medicamento")
    public MedicationScheduleResource changeResponsible(
            @Parameter(hidden = true) Principal principal,
            @PathVariable Long medicationId,
            @RequestBody ChangeMedicationResponsibleResource resource) {
        var command = new ChangeMedicationResponsibleCommand(userIdOf(principal), medicationId, resource.caregiverId());
        var activity = commandService.handle(command);
        return toResource(activity);
    }

    private MedicationScheduleResource toResource(CareActivity activity) {
        Long caregiverId = activity.getActivityAssignments().getActivityAssignments().isEmpty()
                ? null : activity.getActivityAssignments().getActivityAssignments().get(0).getCaregiverId();
        String name = "";
        if (caregiverId != null) {
            var names = resolveCaregiverNames(List.of(activity));
            name = names.getOrDefault(caregiverId, "");
        }
        return MedicationScheduleResourceFromEntityAssembler.toResourceFromEntity(activity, name, objectMapper);
    }

    private Map<Long, String> resolveCaregiverNames(List<CareActivity> activities) {
        var map = new HashMap<Long, String>();
        var personIds = activities.stream().map(CareActivity::getAssistedPersonProfileId).distinct().toList();
        if (personIds.isEmpty()) return map;

        var memberships = careMembershipRepository.findActiveByAssistedPersonProfileIds(personIds);
        var profileIds = memberships.stream().map(CareMembership::getMemberPersonProfileId).collect(java.util.stream.Collectors.toSet());
        var namesByProfile = externalProfilesService.fetchPersonNames(profileIds);

        for (CareMembership membership : memberships) {
            String name = namesByProfile.get(membership.getMemberPersonProfileId());
            if (name != null) {
                map.put(membership.getId(), name);
            }
        }
        return map;
    }

    private static UserId userIdOf(Principal principal) {
        return new UserId(Long.valueOf(principal.getName()));
    }
}
