package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.*;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByRelativeIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareActivitiesByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareActivityByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.domain.services.CareActivityCommandService;
import com.resolum.alivia.assistance.domain.services.CareActivityQueryService;
import com.resolum.alivia.assistance.domain.services.RelativeQueryService;
import com.resolum.alivia.assistance.interfaces.rest.resources.*;
import com.resolum.alivia.assistance.interfaces.rest.transform.CareActivityResourceAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/activities")
@Tag(name = "Care Activities", description = "Endpoints for managing care activities and assignments")
public class CareActivitiesController {

    private final CareActivityCommandService commandService;
    private final CareActivityQueryService queryService;
    private final RelativeQueryService relativeQueryService;
    private final AssistedPersonQueryService assistedPersonQueryService;

    public CareActivitiesController(CareActivityCommandService commandService,
                                   CareActivityQueryService queryService,
                                   RelativeQueryService relativeQueryService,
                                   AssistedPersonQueryService assistedPersonQueryService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.relativeQueryService = relativeQueryService;
        this.assistedPersonQueryService = assistedPersonQueryService;
    }

    private Long resolveRelativeId(Long relativeId, Principal principal) {
        if (relativeId != null) return relativeId;
        if (principal != null) {
            try {
                UserId userId = new UserId(Long.valueOf(principal.getName()));
                return relativeQueryService.handle(new GetRelativeByUserIdQuery(userId)).map(Relative::getId).orElse(1L);
            } catch (Exception ignored) {}
        }
        return 1L;
    }

    @PostMapping
    public ResponseEntity<Long> createCareActivity(
            Principal principal,
            @RequestBody CreateCareActivityResource resource) {
        Long relativeId = resolveRelativeId(resource.relativeId(), principal);
        Long assistedPersonId = resource.assistedPersonId();
        if (assistedPersonId == null) {
            var persons = assistedPersonQueryService.handle(new GetAssistedPersonsByRelativeIdQuery(relativeId));
            assistedPersonId = persons.isEmpty() ? 1L : persons.get(0).getId();
        }

        CreateCareActivityCommand command = new CreateCareActivityCommand(
                relativeId,
                assistedPersonId,
                resource.type(),
                resource.title(),
                resource.description()
        );
        Long id = commandService.handle(command);
        return new ResponseEntity<>(id, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Long> updateCareActivity(
            @PathVariable Long id,
            Principal principal,
            @RequestBody UpdateCareActivityResource resource) {
        Long relativeId = resolveRelativeId(resource.relativeId(), principal);
        UpdateCareActivityCommand command = new UpdateCareActivityCommand(
                id,
                relativeId,
                resource.type(),
                resource.title(),
                resource.description()
        );
        Long updatedId = commandService.handle(command);
        return ResponseEntity.ok(updatedId);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Long> activateCareActivity(
            @PathVariable Long id,
            @RequestParam(required = false) Long relativeId,
            Principal principal) {
        Long effRelativeId = resolveRelativeId(relativeId, principal);
        Long resultId = commandService.handle(new ActivateCareActivityCommand(id, effRelativeId));
        return ResponseEntity.ok(resultId);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Long> deactivateCareActivity(
            @PathVariable Long id,
            @RequestParam(required = false) Long relativeId,
            Principal principal) {
        Long effRelativeId = resolveRelativeId(relativeId, principal);
        Long resultId = commandService.handle(new DeactivateCareActivityCommand(id, effRelativeId));
        return ResponseEntity.ok(resultId);
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<Long> assignCareActivity(
            @PathVariable Long id,
            Principal principal,
            @RequestBody AssignCareActivityResource resource) {
        Long relativeId = resolveRelativeId(resource.relativeId(), principal);
        AssignCareActivityCommand command = new AssignCareActivityCommand(
                id,
                relativeId,
                resource.caregiverId(),
                resource.scheduleType(),
                resource.days(),
                resource.date(),
                resource.startTime(),
                resource.endTime()
        );
        Long resultId = commandService.handle(command);
        return new ResponseEntity<>(resultId, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/assignments/{assignmentId}")
    public ResponseEntity<Long> updateAssignment(
            @PathVariable Long id,
            @PathVariable Long assignmentId,
            Principal principal,
            @RequestBody UpdateActivityAssignmentResource resource) {
        Long relativeId = resolveRelativeId(resource.relativeId(), principal);
        UpdateActivityAssignmentCommand command = new UpdateActivityAssignmentCommand(
                id,
                assignmentId,
                relativeId,
                resource.caregiverId(),
                resource.scheduleType(),
                resource.days(),
                resource.date(),
                resource.startTime(),
                resource.endTime()
        );
        Long resultId = commandService.handle(command);
        return ResponseEntity.ok(resultId);
    }

    @PostMapping("/{id}/assignments/{assignmentId}/activate")
    public ResponseEntity<Long> activateAssignment(
            @PathVariable Long id,
            @PathVariable Long assignmentId,
            @RequestParam(required = false) Long relativeId,
            Principal principal) {
        Long effRelativeId = resolveRelativeId(relativeId, principal);
        Long resultId = commandService.handle(new ActivateActivityAssignmentCommand(id, assignmentId, effRelativeId));
        return ResponseEntity.ok(resultId);
    }

    @PostMapping("/{id}/assignments/{assignmentId}/deactivate")
    public ResponseEntity<Long> deactivateAssignment(
            @PathVariable Long id,
            @PathVariable Long assignmentId,
            @RequestParam(required = false) Long relativeId,
            Principal principal) {
        Long effRelativeId = resolveRelativeId(relativeId, principal);
        Long resultId = commandService.handle(new DeactivateActivityAssignmentCommand(id, assignmentId, effRelativeId));
        return ResponseEntity.ok(resultId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CareActivityResource> getCareActivityById(@PathVariable Long id) {
        return queryService.handle(new GetCareActivityByIdQuery(id))
                .map(CareActivityResourceAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/assisted-person/{assistedPersonId}")
    public ResponseEntity<List<CareActivityResource>> getCareActivitiesByAssistedPersonId(@PathVariable Long assistedPersonId) {
        List<CareActivityResource> list = queryService.handle(new GetCareActivitiesByAssistedPersonIdQuery(assistedPersonId))
                .stream()
                .map(CareActivityResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }
}
