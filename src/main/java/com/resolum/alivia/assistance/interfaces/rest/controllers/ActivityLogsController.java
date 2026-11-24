package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.commands.CancelActivityOccurrenceCommand;
import com.resolum.alivia.assistance.domain.model.commands.CompleteActivityCommand;
import com.resolum.alivia.assistance.domain.model.queries.*;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.domain.services.ActivityLogCommandService;
import com.resolum.alivia.assistance.domain.services.ActivityLogQueryService;
import com.resolum.alivia.assistance.interfaces.rest.CallerIdentity;
import com.resolum.alivia.assistance.interfaces.rest.resources.ActivityLogResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CancelActivityOccurrenceResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CompleteActivityResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.ActivityLogResourceAssembler;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/activity-logs")
@Tag(name = "Activity Logs", description = "Endpoints for history and logging of executed activities")
public class ActivityLogsController {

    private final ActivityLogCommandService commandService;
    private final ActivityLogQueryService queryService;
    private final AssistanceAccessService accessService;

    public ActivityLogsController(ActivityLogCommandService commandService,
                                  ActivityLogQueryService queryService,
                                  AssistanceAccessService accessService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.accessService = accessService;
    }

    @PostMapping("/complete")
    public ResponseEntity<Long> completeActivity(
            Principal principal,
            @RequestBody CompleteActivityResource resource) {
        Long userId = CallerIdentity.requireUserId(principal);
        accessService.requireOperateOccurrence(resource.careActivityId(), resource.assignmentId(), userId);

        // Who completes the activity is the caller; the caregiver user id of the body is not trusted.
        CompleteActivityCommand command = new CompleteActivityCommand(
                resource.careActivityId(),
                resource.assignmentId(),
                resource.date(),
                userId,
                resource.notes()
        );
        Long id = commandService.handle(command);
        return new ResponseEntity<>(id, HttpStatus.CREATED);
    }

    @PostMapping("/cancel")
    public ResponseEntity<Long> cancelActivity(
            Principal principal,
            @RequestBody CancelActivityOccurrenceResource resource) {
        var activity = accessService.requireOperateOccurrence(
                resource.careActivityId(), resource.assignmentId(), CallerIdentity.requireUserId(principal));

        CancelActivityOccurrenceCommand command = new CancelActivityOccurrenceCommand(
                resource.careActivityId(),
                resource.assignmentId(),
                resource.date(),
                activity.getAssistedPerson().getRelative().getId()
        );
        Long id = commandService.handle(command);
        return new ResponseEntity<>(id, HttpStatus.CREATED);
    }

    @GetMapping("/assisted-persons/{assistedPersonId}")
    public ResponseEntity<List<ActivityLogResource>> getActivityLogsByAssistedPersonId(
            @PathVariable Long assistedPersonId,
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        accessService.requireRead(assistedPersonId, CallerIdentity.requireUserId(principal));
        List<ActivityLogResource> logs = queryService.handle(new GetActivityLogsByAssistedPersonIdQuery(assistedPersonId, from, to))
                .stream()
                .map(ActivityLogResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/caregivers/{caregiverId}")
    public ResponseEntity<List<ActivityLogResource>> getActivityLogsByCaregiverId(
            @PathVariable Long caregiverId,
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        accessService.requireReadCaregiver(caregiverId, CallerIdentity.requireUserId(principal));
        List<ActivityLogResource> logs = queryService.handle(new GetActivityLogsByCaregiverIdQuery(caregiverId, from, to))
                .stream()
                .map(ActivityLogResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(logs);
    }
}
