package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.CancelActivityOccurrenceCommand;
import com.resolum.alivia.assistance.domain.model.commands.CompleteActivityCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetActivityLogsByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetActivityLogsByCaregiverIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.services.ActivityLogCommandService;
import com.resolum.alivia.assistance.domain.services.ActivityLogQueryService;
import com.resolum.alivia.assistance.domain.services.RelativeQueryService;
import com.resolum.alivia.assistance.interfaces.rest.resources.ActivityLogResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CancelActivityOccurrenceResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CompleteActivityResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.ActivityLogResourceAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
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
    private final RelativeQueryService relativeQueryService;

    public ActivityLogsController(ActivityLogCommandService commandService,
                                  ActivityLogQueryService queryService,
                                  RelativeQueryService relativeQueryService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.relativeQueryService = relativeQueryService;
    }

    @PostMapping("/complete")
    public ResponseEntity<Long> completeActivity(
            Principal principal,
            @RequestBody CompleteActivityResource resource) {
        Long caregiverUserId = resource.caregiverUserId();
        if (caregiverUserId == null && principal != null) {
            try {
                caregiverUserId = Long.valueOf(principal.getName());
            } catch (Exception ignored) {}
        }
        if (caregiverUserId == null) {
            caregiverUserId = 1L;
        }

        CompleteActivityCommand command = new CompleteActivityCommand(
                resource.careActivityId(),
                resource.assignmentId(),
                resource.date(),
                caregiverUserId,
                resource.notes()
        );
        Long id = commandService.handle(command);
        return new ResponseEntity<>(id, HttpStatus.CREATED);
    }

    @PostMapping("/cancel")
    public ResponseEntity<Long> cancelActivity(
            Principal principal,
            @RequestBody CancelActivityOccurrenceResource resource) {
        Long relativeId = resource.relativeId();
        if (relativeId == null && principal != null) {
            try {
                Long userId = Long.valueOf(principal.getName());
                relativeId = relativeQueryService.handle(new GetRelativeByUserIdQuery(userId)).map(Relative::getId).orElse(1L);
            } catch (Exception ignored) {}
        }
        if (relativeId == null) {
            relativeId = 1L;
        }

        CancelActivityOccurrenceCommand command = new CancelActivityOccurrenceCommand(
                resource.careActivityId(),
                resource.assignmentId(),
                resource.date(),
                relativeId
        );
        Long id = commandService.handle(command);
        return new ResponseEntity<>(id, HttpStatus.CREATED);
    }

    @GetMapping("/assisted-persons/{assistedPersonId}")
    public ResponseEntity<List<ActivityLogResource>> getActivityLogsByAssistedPersonId(
            @PathVariable Long assistedPersonId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<ActivityLogResource> logs = queryService.handle(new GetActivityLogsByAssistedPersonIdQuery(assistedPersonId, from, to))
                .stream()
                .map(ActivityLogResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/caregivers/{caregiverId}")
    public ResponseEntity<List<ActivityLogResource>> getActivityLogsByCaregiverId(
            @PathVariable Long caregiverId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<ActivityLogResource> logs = queryService.handle(new GetActivityLogsByCaregiverIdQuery(caregiverId, from, to))
                .stream()
                .map(ActivityLogResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(logs);
    }
}
