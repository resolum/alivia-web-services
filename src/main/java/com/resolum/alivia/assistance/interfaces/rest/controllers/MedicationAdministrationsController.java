package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.commands.CancelActivityOccurrenceCommand;
import com.resolum.alivia.assistance.domain.model.commands.CompleteActivityCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetActivityLogsByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAllAssistedPersonsQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.ActivityLogStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.assistance.domain.services.ActivityLogCommandService;
import com.resolum.alivia.assistance.domain.services.ActivityLogQueryService;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.domain.services.CareActivityQueryService;
import com.resolum.alivia.assistance.interfaces.rest.resources.CareAgendaItemResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.MedicationAdministrationResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.RecordMedicationOutcomeResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.CareAgendaItemResourceAssembler;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/medication-administrations")
@Tag(name = "Medication Administrations", description = "Endpoints for medication dose tracking and outcomes")
public class MedicationAdministrationsController {

    private final CareActivityQueryService careActivityQueryService;
    private final AssistedPersonQueryService assistedPersonQueryService;
    private final ActivityLogQueryService activityLogQueryService;
    private final ActivityLogCommandService activityLogCommandService;

    public MedicationAdministrationsController(
            CareActivityQueryService careActivityQueryService,
            AssistedPersonQueryService assistedPersonQueryService,
            ActivityLogQueryService activityLogQueryService,
            ActivityLogCommandService activityLogCommandService) {
        this.careActivityQueryService = careActivityQueryService;
        this.assistedPersonQueryService = assistedPersonQueryService;
        this.activityLogQueryService = activityLogQueryService;
        this.activityLogCommandService = activityLogCommandService;
    }

    @GetMapping("/{administrationId}")
    public ResponseEntity<MedicationAdministrationResource> getAdministration(@PathVariable String administrationId) {
        OccurrenceKey key = parseAdministrationId(administrationId);
        if (key == null) {
            return ResponseEntity.notFound().build();
        }
        Optional<AgendaMatch> matchOpt = findOccurrence(key.assignmentId(), key.date());
        if (matchOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toResource(administrationId, matchOpt.get(), key.date()));
    }

    @PostMapping("/{administrationId}/outcomes")
    public ResponseEntity<MedicationAdministrationResource> recordOutcome(
            @PathVariable String administrationId,
            @RequestBody RecordMedicationOutcomeResource resource,
            Principal principal) {
        OccurrenceKey key = parseAdministrationId(administrationId);
        if (key == null || resource == null || resource.result() == null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<AgendaMatch> matchOpt = findOccurrence(key.assignmentId(), key.date());
        if (matchOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AgendaMatch match = matchOpt.get();
        Long userId = null;
        if (principal != null) {
            try {
                userId = Long.valueOf(principal.getName());
            } catch (Exception ignored) {
            }
        }

        if ("ADMINISTERED".equalsIgnoreCase(resource.result())) {
            activityLogCommandService.handle(new CompleteActivityCommand(
                    match.item().careActivityId(),
                    key.assignmentId(),
                    key.date(),
                    userId,
                    resource.reason()
            ));
        } else if ("OMITTED".equalsIgnoreCase(resource.result())) {
            activityLogCommandService.handle(new CancelActivityOccurrenceCommand(
                    match.item().careActivityId(),
                    key.assignmentId(),
                    key.date(),
                    null
            ));
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(toResource(administrationId, match, key.date()));
    }

    private Optional<AgendaMatch> findOccurrence(Long assignmentId, LocalDate date) {
        List<AssistedPerson> persons = assistedPersonQueryService.handle(new GetAllAssistedPersonsQuery());
        for (AssistedPerson person : persons) {
            List<AgendaItem> items = careActivityQueryService.handle(
                    new GetAgendaByAssistedPersonIdQuery(person.getId(), date, date));
            for (AgendaItem item : items) {
                if (Objects.equals(item.assignmentId(), assignmentId) && Objects.equals(item.date(), date)) {
                    return Optional.of(new AgendaMatch(person.getId(), item));
                }
            }
        }
        return Optional.empty();
    }

    private MedicationAdministrationResource toResource(String administrationId, AgendaMatch match, LocalDate date) {
        CareAgendaItemResource agendaRes = CareAgendaItemResourceAssembler.toResourceFromEntity(match.item());

        Optional<ActivityLog> existingLog = activityLogQueryService
                .handle(new GetActivityLogsByAssistedPersonIdQuery(match.assistedPersonId(), date, date))
                .stream()
                .filter(logEntry -> Objects.equals(logEntry.getAssignmentId(), match.item().assignmentId())
                        && Objects.equals(logEntry.getDate(), date))
                .findFirst();

        MedicationAdministrationResource.OutcomeResource outcome = existingLog.map(logEntry -> {
            String result = logEntry.getStatus() == ActivityLogStatus.COMPLETED ? "ADMINISTERED" : "OMITTED";
            return new MedicationAdministrationResource.OutcomeResource(
                    result,
                    logEntry.getCompletedAt(),
                    logEntry.getNotes()
            );
        }).orElse(null);

        var assistedRef = agendaRes.assistedPerson() != null
                ? new MedicationAdministrationResource.AssistedPersonRef(
                        agendaRes.assistedPerson().id(),
                        agendaRes.assistedPerson().name())
                : new MedicationAdministrationResource.AssistedPersonRef(match.assistedPersonId(), null);

        var responsibleRef = agendaRes.responsible() != null
                ? new MedicationAdministrationResource.ResponsibleRef(
                        agendaRes.responsible().type(),
                        agendaRes.responsible().id(),
                        agendaRes.responsible().name())
                : null;

        return new MedicationAdministrationResource(
                administrationId,
                agendaRes.activity().title(),
                assistedRef,
                agendaRes.date(),
                agendaRes.startTime(),
                agendaRes.endTime(),
                responsibleRef,
                agendaRes.status().name(),
                outcome
        );
    }

    private OccurrenceKey parseAdministrationId(String administrationId) {
        if (administrationId == null || !administrationId.contains("-")) {
            return null;
        }
        int idx = administrationId.lastIndexOf('-');
        try {
            Long assignmentId = Long.valueOf(administrationId.substring(0, idx));
            LocalDate date = LocalDate.parse(administrationId.substring(idx + 1), DateTimeFormatter.BASIC_ISO_DATE);
            return new OccurrenceKey(assignmentId, date);
        } catch (Exception e) {
            return null;
        }
    }

    private record OccurrenceKey(Long assignmentId, LocalDate date) {}

    private record AgendaMatch(Long assistedPersonId, AgendaItem item) {}
}
