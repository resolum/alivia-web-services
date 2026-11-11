package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.exceptions.RelativeNotFoundException;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.AcceptInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.CreateInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.ResendInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.RevokeInvitationCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetAllAssistedPersonsQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByRelativeIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetInvitationByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetInvitationsByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetPendingInvitationsByCaregiverUserIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.domain.services.InvitationCommandService;
import com.resolum.alivia.assistance.domain.services.InvitationQueryService;
import com.resolum.alivia.assistance.domain.services.RelativeQueryService;
import com.resolum.alivia.assistance.interfaces.rest.resources.AcceptInvitationResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CreateInvitationResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.InvitationResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.InvitationResourceAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/invitations")
@Tag(name = "Invitations", description = "Endpoints for caregiver invitations")
public class InvitationsController {

    private final InvitationCommandService commandService;
    private final InvitationQueryService queryService;
    private final RelativeQueryService relativeQueryService;
    private final AssistedPersonQueryService assistedPersonQueryService;

    public InvitationsController(InvitationCommandService commandService,
                                 InvitationQueryService queryService,
                                 RelativeQueryService relativeQueryService,
                                 AssistedPersonQueryService assistedPersonQueryService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.relativeQueryService = relativeQueryService;
        this.assistedPersonQueryService = assistedPersonQueryService;
    }

    @PostMapping
    public ResponseEntity<Long> createInvitation(
            Principal principal,
            @RequestBody CreateInvitationResource resource) {
        Long relativeId = null;
        if (principal != null) {
            try {
                UserId userId = new UserId(Long.valueOf(principal.getName()));
                relativeId = relativeQueryService.handle(new GetRelativeByUserIdQuery(userId)).map(Relative::getId).orElse(null);
            } catch (Exception ignored) {
            }
        }
        if (relativeId == null && resource.relativeId() != null) {
            if (relativeQueryService.handle(new GetRelativeByIdQuery(resource.relativeId())).isPresent()) {
                relativeId = resource.relativeId();
            }
        }
        if (relativeId == null) {
            var defaultRelative = relativeQueryService.handle(new GetRelativeByIdQuery(1L));
            if (defaultRelative.isPresent()) {
                relativeId = 1L;
            } else {
                throw new RelativeNotFoundException(1L);
            }
        }

        Long assistedPersonId = resource.assistedPersonId();
        if (assistedPersonId == null || assistedPersonQueryService.handle(new GetAssistedPersonByIdQuery(assistedPersonId)).isEmpty()) {
            var persons = assistedPersonQueryService.handle(new GetAssistedPersonsByRelativeIdQuery(relativeId));
            assistedPersonId = persons.isEmpty() ? 1L : persons.get(0).getId();
        } else {
            var apOpt = assistedPersonQueryService.handle(new GetAssistedPersonByIdQuery(assistedPersonId));
            if (apOpt.isPresent() && !apOpt.get().getRelative().getId().equals(relativeId)) {
                var persons = assistedPersonQueryService.handle(new GetAssistedPersonsByRelativeIdQuery(relativeId));
                if (!persons.isEmpty()) {
                    assistedPersonId = persons.get(0).getId();
                }
            }
        }

        CreateInvitationCommand command = new CreateInvitationCommand(
                relativeId,
                assistedPersonId,
                resource.inviteeEmail());
        Long id = commandService.handle(command);
        return new ResponseEntity<>(id, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<Long> acceptInvitation(
            @PathVariable Long id,
            @RequestBody(required = false) AcceptInvitationResource resource,
            Principal principal) {
        Long caregiverUserId = resource != null ? resource.caregiverUserId() : null;
        if (caregiverUserId == null && principal != null) {
            try {
                caregiverUserId = Long.valueOf(principal.getName());
            } catch (Exception ignored) {}
        }
        if (caregiverUserId == null) {
            caregiverUserId = 1L;
        }
        AcceptInvitationCommand command = new AcceptInvitationCommand(id, caregiverUserId);
        Long resultId = commandService.handle(command);
        return ResponseEntity.ok(resultId);
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<Long> revokeInvitation(
            @PathVariable Long id,
            @RequestParam(required = false) Long relativeId,
            Principal principal) {
        Long effectiveRelativeId = null;
        if (principal != null) {
            try {
                UserId userId = new UserId(Long.valueOf(principal.getName()));
                effectiveRelativeId = relativeQueryService.handle(new GetRelativeByUserIdQuery(userId)).map(Relative::getId).orElse(null);
            } catch (Exception ignored) {}
        }
        if (effectiveRelativeId == null) {
            effectiveRelativeId = relativeId != null ? relativeId : 1L;
        }
        RevokeInvitationCommand command = new RevokeInvitationCommand(id, effectiveRelativeId);
        Long resultId = commandService.handle(command);
        return ResponseEntity.ok(resultId);
    }

    @PostMapping("/{id}/resend")
    public ResponseEntity<Long> resendInvitation(
            @PathVariable Long id,
            @RequestParam(required = false) Long relativeId,
            Principal principal) {
        Long effectiveRelativeId = null;
        if (principal != null) {
            try {
                UserId userId = new UserId(Long.valueOf(principal.getName()));
                effectiveRelativeId = relativeQueryService.handle(new GetRelativeByUserIdQuery(userId)).map(Relative::getId).orElse(null);
            } catch (Exception ignored) {}
        }
        if (effectiveRelativeId == null) {
            effectiveRelativeId = relativeId != null ? relativeId : 1L;
        }
        ResendInvitationCommand command = new ResendInvitationCommand(id, effectiveRelativeId);
        Long resultId = commandService.handle(command);
        return ResponseEntity.ok(resultId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvitationResource> getInvitationById(@PathVariable Long id) {
        return queryService.handle(new GetInvitationByIdQuery(id))
                .map(InvitationResourceAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<InvitationResource>> getInvitations(
            Principal principal,
            @RequestParam(required = false) Long assistedPersonId) {
        if (assistedPersonId != null) {
            List<InvitationResource> list = queryService
                    .handle(new GetInvitationsByAssistedPersonIdQuery(assistedPersonId))
                    .stream()
                    .map(InvitationResourceAssembler::toResourceFromEntity)
                    .toList();
            return ResponseEntity.ok(list);
        }
        if (principal != null) {
            try {
                UserId userId = new UserId(Long.valueOf(principal.getName()));
                var relativeOpt = relativeQueryService.handle(new GetRelativeByUserIdQuery(userId));
                if (relativeOpt.isPresent()) {
                    var persons = assistedPersonQueryService.handle(new GetAssistedPersonsByRelativeIdQuery(relativeOpt.get().getId()));
                    List<InvitationResource> all = new ArrayList<>();
                    for (var person : persons) {
                        all.addAll(queryService.handle(new GetInvitationsByAssistedPersonIdQuery(person.getId()))
                                .stream()
                                .map(InvitationResourceAssembler::toResourceFromEntity)
                                .toList());
                    }
                    return ResponseEntity.ok(all);
                }
            } catch (Exception e) {
                log.warn("Could not find invitations by principal {}: {}", principal.getName(), e.getMessage());
            }
        }
        var persons = assistedPersonQueryService.handle(new GetAllAssistedPersonsQuery());
        List<InvitationResource> all = new ArrayList<>();
        for (var person : persons) {
            all.addAll(queryService.handle(new GetInvitationsByAssistedPersonIdQuery(person.getId()))
                    .stream()
                    .map(InvitationResourceAssembler::toResourceFromEntity)
                    .toList());
        }
        return ResponseEntity.ok(all);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<InvitationResource>> getPendingInvitations(@RequestParam Long caregiverUserId) {
        List<InvitationResource> list = queryService
                .handle(new GetPendingInvitationsByCaregiverUserIdQuery(caregiverUserId))
                .stream()
                .map(InvitationResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }
}
