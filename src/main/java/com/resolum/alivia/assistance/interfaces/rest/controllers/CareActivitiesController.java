package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.commands.*;
import com.resolum.alivia.assistance.domain.model.queries.*;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.domain.services.CareActivityCommandService;
import com.resolum.alivia.assistance.domain.services.CareActivityQueryService;
import com.resolum.alivia.assistance.interfaces.rest.CallerIdentity;
import com.resolum.alivia.assistance.interfaces.rest.resources.*;
import com.resolum.alivia.assistance.interfaces.rest.transform.CareActivityResourceAssembler;
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
    private final AssistanceAccessService accessService;

    public CareActivitiesController(CareActivityCommandService commandService,
                                   CareActivityQueryService queryService,
                                   AssistanceAccessService accessService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.accessService = accessService;
    }

    /**
     * The activities are changed on behalf of the relative in charge of the person. Who is calling was already
     * checked, so this id is the one of the person's relative and never one sent by the client.
     */
    private static Long relativeInChargeOf(AssistedPerson person) {
        return person.getRelative().getId();
    }

    @PostMapping
    public ResponseEntity<Long> createCareActivity(
            Principal principal,
            @RequestBody CreateCareActivityResource resource) {
        var person = accessService.requireOperate(resource.assistedPersonId(), CallerIdentity.requireUserId(principal));

        CreateCareActivityCommand command = new CreateCareActivityCommand(
                relativeInChargeOf(person),
                person.getId(),
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
        var activity = accessService.requireOperateActivity(id, CallerIdentity.requireUserId(principal));
        UpdateCareActivityCommand command = new UpdateCareActivityCommand(
                id,
                relativeInChargeOf(activity.getAssistedPerson()),
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
            Principal principal) {
        var activity = accessService.requireOperateActivity(id, CallerIdentity.requireUserId(principal));
        Long resultId = commandService.handle(
                new ActivateCareActivityCommand(id, relativeInChargeOf(activity.getAssistedPerson())));
        return ResponseEntity.ok(resultId);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Long> deactivateCareActivity(
            @PathVariable Long id,
            Principal principal) {
        var activity = accessService.requireOperateActivity(id, CallerIdentity.requireUserId(principal));
        Long resultId = commandService.handle(
                new DeactivateCareActivityCommand(id, relativeInChargeOf(activity.getAssistedPerson())));
        return ResponseEntity.ok(resultId);
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<Long> assignCareActivity(
            @PathVariable Long id,
            Principal principal,
            @RequestBody AssignCareActivityResource resource) {
        var activity = accessService.requireOperateActivity(id, CallerIdentity.requireUserId(principal));
        AssignCareActivityCommand command = new AssignCareActivityCommand(
                id,
                relativeInChargeOf(activity.getAssistedPerson()),
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
        var activity = accessService.requireOperateActivity(id, CallerIdentity.requireUserId(principal));
        UpdateActivityAssignmentCommand command = new UpdateActivityAssignmentCommand(
                id,
                assignmentId,
                relativeInChargeOf(activity.getAssistedPerson()),
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
            Principal principal) {
        var activity = accessService.requireOperateActivity(id, CallerIdentity.requireUserId(principal));
        Long resultId = commandService.handle(new ActivateActivityAssignmentCommand(
                id, assignmentId, relativeInChargeOf(activity.getAssistedPerson())));
        return ResponseEntity.ok(resultId);
    }

    @PostMapping("/{id}/assignments/{assignmentId}/deactivate")
    public ResponseEntity<Long> deactivateAssignment(
            @PathVariable Long id,
            @PathVariable Long assignmentId,
            Principal principal) {
        var activity = accessService.requireOperateActivity(id, CallerIdentity.requireUserId(principal));
        Long resultId = commandService.handle(new DeactivateActivityAssignmentCommand(
                id, assignmentId, relativeInChargeOf(activity.getAssistedPerson())));
        return ResponseEntity.ok(resultId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CareActivityResource> getCareActivityById(@PathVariable Long id, Principal principal) {
        var activity = accessService.requireReadActivity(id, CallerIdentity.requireUserId(principal));
        return ResponseEntity.ok(CareActivityResourceAssembler.toResourceFromEntity(activity));
    }

    @GetMapping("/assisted-person/{assistedPersonId}")
    public ResponseEntity<List<CareActivityResource>> getCareActivitiesByAssistedPersonId(
            @PathVariable Long assistedPersonId, Principal principal) {
        accessService.requireRead(assistedPersonId, CallerIdentity.requireUserId(principal));
        List<CareActivityResource> list = queryService.handle(new GetCareActivitiesByAssistedPersonIdQuery(assistedPersonId))
                .stream()
                .map(CareActivityResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }
}
