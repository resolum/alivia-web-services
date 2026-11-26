package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.domain.model.commands.AnswerHandoverCommand;
import com.resolum.alivia.assistance.domain.model.commands.EscalateHandoverCommand;
import com.resolum.alivia.assistance.domain.model.commands.ProposeSubstituteCommand;
import com.resolum.alivia.assistance.domain.model.commands.RequestHandoverCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetHandoversQuery;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.domain.services.HandoverCommandService;
import com.resolum.alivia.assistance.domain.services.HandoverQueryService;
import com.resolum.alivia.assistance.interfaces.rest.CallerIdentity;
import com.resolum.alivia.assistance.interfaces.rest.resources.AnswerHandoverResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.HandoverResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.ProposeSubstituteResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.RequestHandoverResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.HandoverResourceAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Care Handovers", description = "Endpoints for caregiver shift handovers and relief")
public class HandoversController {

    private final HandoverCommandService commandService;
    private final HandoverQueryService queryService;
    private final AssistanceAccessService accessService;

    public HandoversController(HandoverCommandService commandService,
            HandoverQueryService queryService,
            AssistanceAccessService accessService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.accessService = accessService;
    }

    @GetMapping({ "/handovers", "/assisted-persons/{assistedPersonId}/handovers" })
    @Operation(summary = "Get shift handovers", description = "Retrieves all handovers for the assisted person")
    public ResponseEntity<List<HandoverResource>> getHandovers(
            @PathVariable(required = false) Long assistedPersonId,
            @RequestParam(required = false) Long assistedPerson,
            Principal principal) {

        Long userId = CallerIdentity.requireUserId(principal);
        Long targetId = assistedPersonId != null ? assistedPersonId : assistedPerson;
        if (targetId == null) {
            // Without a person: the first one the caller takes care of, never one chosen by the service.
            var readable = accessService.readableBy(userId);
            if (readable.isEmpty()) return ResponseEntity.ok(List.of());
            targetId = readable.getFirst().getId();
        }
        accessService.requireRead(targetId, userId);
        List<Handover> handovers = queryService.handle(new GetHandoversQuery(targetId, new UserId(userId)));
        List<HandoverResource> resources = handovers.stream()
                .map(HandoverResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @PostMapping("/handovers/request")
    @Operation(summary = "Request shift handover")
    public ResponseEntity<HandoverResource> requestHandover(@RequestBody RequestHandoverResource resource,
                                                            Principal principal) {
        Long userId = CallerIdentity.requireUserId(principal);
        Long fromShiftId = Long.parseLong(resource.fromShiftId());
        Long toShiftId = Long.parseLong(resource.toShiftId());
        accessService.requireOperateShift(fromShiftId, userId);
        accessService.requireOperateShift(toShiftId, userId);

        Handover handover = commandService.handle(new RequestHandoverCommand(
                fromShiftId,
                toShiftId,
                resource.attended(),
                resource.pending()));

        return ResponseEntity.status(HttpStatus.CREATED).body(HandoverResourceAssembler.toResourceFromEntity(handover));
    }

    private Long parseOptionalLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            String digits = raw.replaceAll("\\D+", "");
            if (!digits.isEmpty()) {
                try {
                    return Long.parseLong(digits);
                } catch (NumberFormatException ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    @PostMapping("/handovers/{id}/answer")
    @Operation(summary = "Answer shift handover (accept or decline)")
    public ResponseEntity<HandoverResource> answerHandover(
            @PathVariable Long id,
            @RequestBody AnswerHandoverResource resource,
            Principal principal) {

        accessService.requireOperateHandover(id, CallerIdentity.requireUserId(principal));
        Long actorId = parseOptionalLong(resource.actorId());

        Handover handover = commandService.handle(new AnswerHandoverCommand(
                id,
                resource.accept(),
                actorId,
                resource.actorName(),
                resource.attended(),
                resource.pending()));

        return ResponseEntity.ok(HandoverResourceAssembler.toResourceFromEntity(handover));
    }

    @PostMapping("/handovers/{id}/substitute")
    @Operation(summary = "Propose substitute for shift handover")
    public ResponseEntity<HandoverResource> proposeSubstitute(
            @PathVariable Long id,
            @RequestBody ProposeSubstituteResource resource,
            Principal principal) {

        Long userId = CallerIdentity.requireUserId(principal);
        accessService.requireOperateHandover(id, userId);
        Long substituteId = parseOptionalLong(resource.substituteCaregiverId());
        if (substituteId == null) {
            substituteId = userId;
        }
        Long actorId = parseOptionalLong(resource.actorId());

        Handover handover = commandService.handle(new ProposeSubstituteCommand(
                id,
                substituteId,
                resource.substituteCaregiverName(),
                actorId));

        return ResponseEntity.ok(HandoverResourceAssembler.toResourceFromEntity(handover));
    }

    @PostMapping("/handovers/{id}/escalate")
    @Operation(summary = "Mark shift handover as escalated")
    public ResponseEntity<HandoverResource> escalateHandover(@PathVariable Long id, Principal principal) {
        accessService.requireOperateHandover(id, CallerIdentity.requireUserId(principal));
        Handover handover = commandService.handle(new EscalateHandoverCommand(id));
        return ResponseEntity.ok(HandoverResourceAssembler.toResourceFromEntity(handover));
    }
}
