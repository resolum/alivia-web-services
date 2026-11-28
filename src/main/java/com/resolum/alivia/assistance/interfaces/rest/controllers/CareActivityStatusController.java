package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.commands.CancelActivityOccurrenceCommand;
import com.resolum.alivia.assistance.domain.model.commands.CompleteActivityCommand;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.assistance.domain.model.queries.GetActivityLogsByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.services.ActivityLogCommandService;
import com.resolum.alivia.assistance.domain.services.ActivityLogQueryService;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.interfaces.rest.CallerIdentity;
import com.resolum.alivia.assistance.interfaces.rest.resources.ActivityLogResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.UpdateCareActivityStatusResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.ActivityLogResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * TS-14 #2: the status of an occurrence of a care activity, as the frontend contract names it. It records the same
 * result as {@code /activity-logs/complete} and {@code /activity-logs/cancel}, with the same rules.
 */
@RestController
@RequestMapping(value = "/api/v1/care-activities", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Care Activities", description = "Status of an occurrence of a care activity")
public class CareActivityStatusController {

    private final ActivityLogCommandService commandService;
    private final ActivityLogQueryService queryService;
    private final AssistanceAccessService accessService;

    public CareActivityStatusController(ActivityLogCommandService commandService,
                                        ActivityLogQueryService queryService,
                                        AssistanceAccessService accessService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.accessService = accessService;
    }

    @PatchMapping("/{activityId}/status")
    @Operation(summary = "Mark an occurrence COMPLETED or CANCELLED",
            description = "200 with the log: who completed it and when. 404 when the activity does not exist, 409 "
                    + "when the occurrence is already closed, 403 when the caller is not its responsible caregiver.")
    public ResponseEntity<ActivityLogResource> updateStatus(@PathVariable Long activityId,
                                                            @RequestBody UpdateCareActivityStatusResource resource,
                                                            Principal principal) {
        if (resource == null || resource.status() == null) {
            throw new IllegalArgumentException("The status is required: COMPLETED or CANCELLED.");
        }
        Long userId = CallerIdentity.requireUserId(principal);
        var activity = accessService.requireOperateActivity(activityId, userId);
        LocalDate date = resource.date() != null ? resource.date() : LocalDate.now();
        Long assignmentId = resource.assignmentId() != null ? resource.assignmentId() : onlyOccurrence(activity
                .occurrencesOn(date).stream().map(ActivityAssignment::getId).toList());

        Long logId = switch (resource.status().strip().toUpperCase(Locale.ROOT)) {
            // Who completes it is the caller, never a field of the body.
            case "COMPLETED" -> commandService.handle(
                    new CompleteActivityCommand(activityId, assignmentId, date, userId, resource.notes()));
            case "CANCELLED", "OMITTED" -> commandService.handle(new CancelActivityOccurrenceCommand(
                    activityId, assignmentId, date, activity.getAssistedPerson().getRelative().getId()));
            default -> throw new IllegalArgumentException("Unknown status: " + resource.status());
        };

        var log = queryService.handle(new GetActivityLogsByAssistedPersonIdQuery(
                        activity.getAssistedPerson().getId(), date, date)).stream()
                .filter(entry -> Objects.equals(entry.getId(), logId))
                .findFirst().orElseThrow();
        return ResponseEntity.ok(ActivityLogResourceAssembler.toResourceFromEntity(log));
    }

    private static Long onlyOccurrence(java.util.List<Long> assignmentIds) {
        if (assignmentIds.size() != 1) {
            throw new IllegalArgumentException("The activity has " + assignmentIds.size()
                    + " occurrences that day: send the assignmentId.");
        }
        return assignmentIds.getFirst();
    }
}
