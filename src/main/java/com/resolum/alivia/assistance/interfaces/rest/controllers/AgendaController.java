package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByCaregiverIdQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.assistance.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.ReminderPriority;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CareActivityRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.CareActivityQueryService;
import com.resolum.alivia.assistance.interfaces.rest.resources.CareAgendaItemResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.CareAgendaItemResourceAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Agenda", description = "Endpoints for daily care schedule and agenda")
public class AgendaController {

    private final CareActivityQueryService careActivityQueryService;
    private final CareActivityRepository careActivityRepository;
    private final AssistedPersonRepository assistedPersonRepository;
    private final RelativeRepository relativeRepository;
    private final CaregiverRepository caregiverRepository;

    public AgendaController(CareActivityQueryService careActivityQueryService,
                            CareActivityRepository careActivityRepository,
                            AssistedPersonRepository assistedPersonRepository,
                            RelativeRepository relativeRepository,
                            CaregiverRepository caregiverRepository) {
        this.careActivityQueryService = careActivityQueryService;
        this.careActivityRepository = careActivityRepository;
        this.assistedPersonRepository = assistedPersonRepository;
        this.relativeRepository = relativeRepository;
        this.caregiverRepository = caregiverRepository;
    }

    @GetMapping(path = {"/care-agenda", "/agenda"})
    @Operation(summary = "Get care agenda for period", description = "Returns care agenda occurrences for given date range")
    public ResponseEntity<List<CareAgendaItemResource>> getCareAgenda(
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long assistedPersonId,
            @RequestParam(required = false) Long caregiverId,
            @RequestParam(required = false) OccurrenceStatus status,
            @RequestParam(required = false) ReminderPriority priority) {

        List<AgendaItem> items = new ArrayList<>();
        if (assistedPersonId != null) {
            items.addAll(careActivityQueryService.handle(new GetAgendaByAssistedPersonIdQuery(assistedPersonId, from, to)));
        } else if (caregiverId != null) {
            items.addAll(careActivityQueryService.handle(new GetAgendaByCaregiverIdQuery(caregiverId, from, to)));
        } else if (principal != null) {
            try {
                UserId userId = new UserId(Long.valueOf(principal.getName()));
                var relativeOpt = relativeRepository.findByUserId(userId);
                if (relativeOpt.isPresent()) {
                    var persons = assistedPersonRepository.findAllByRelativeId(relativeOpt.get().getId());
                    for (var person : persons) {
                        items.addAll(careActivityQueryService.handle(new GetAgendaByAssistedPersonIdQuery(person.getId(), from, to)));
                    }
                } else {
                    var caregivers = caregiverRepository.findAllByUserId(userId);
                    for (var caregiver : caregivers) {
                        items.addAll(careActivityQueryService.handle(new GetAgendaByCaregiverIdQuery(caregiver.getId(), from, to)));
                    }
                }
            } catch (Exception e) {
                log.warn("Could not resolve agenda for principal {}: {}", principal.getName(), e.getMessage());
            }
        }

        if (items.isEmpty() && assistedPersonId == null && caregiverId == null) {
            var activeActivities = careActivityRepository.findAllActive();
            var personIds = activeActivities.stream()
                    .filter(a -> a.getAssistedPerson() != null)
                    .map(a -> a.getAssistedPerson().getId())
                    .distinct()
                    .toList();
            for (Long pId : personIds) {
                items.addAll(careActivityQueryService.handle(new GetAgendaByAssistedPersonIdQuery(pId, from, to)));
            }
        }

        List<CareAgendaItemResource> resources = items.stream()
                .filter(item -> status == null || item.status() == status)
                .filter(item -> priority == null || item.priority() == priority)
                .sorted(Comparator.comparing(AgendaItem::startsAt))
                .map(CareAgendaItemResourceAssembler::toResourceFromEntity)
                .toList();

        return ResponseEntity.ok(resources);
    }

    @GetMapping(path = {"/agenda/assisted-persons/{assistedPersonId}", "/care-agenda/assisted-persons/{assistedPersonId}"})
    public ResponseEntity<List<CareAgendaItemResource>> getAgendaByAssistedPersonId(
            @PathVariable Long assistedPersonId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<AgendaItem> agenda = careActivityQueryService.handle(new GetAgendaByAssistedPersonIdQuery(assistedPersonId, from, to));
        return ResponseEntity.ok(agenda.stream().map(CareAgendaItemResourceAssembler::toResourceFromEntity).toList());
    }

    @GetMapping(path = {"/agenda/caregivers/{caregiverId}", "/care-agenda/caregivers/{caregiverId}"})
    public ResponseEntity<List<CareAgendaItemResource>> getAgendaByCaregiverId(
            @PathVariable Long caregiverId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<AgendaItem> agenda = careActivityQueryService.handle(new GetAgendaByCaregiverIdQuery(caregiverId, from, to));
        return ResponseEntity.ok(agenda.stream().map(CareAgendaItemResourceAssembler::toResourceFromEntity).toList());
    }
}
