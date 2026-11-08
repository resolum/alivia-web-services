package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.AcceptInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.CreateInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.ResendInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.RevokeInvitationCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetInvitationByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetInvitationsByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetPendingInvitationsByCaregiverUserIdQuery;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.InvitationCommandService;
import com.resolum.alivia.assistance.domain.services.InvitationQueryService;
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
    private final RelativeRepository relativeRepository;
    private final AssistedPersonRepository assistedPersonRepository;

    public InvitationsController(InvitationCommandService commandService,
                                InvitationQueryService queryService,
                                RelativeRepository relativeRepository,
                                AssistedPersonRepository assistedPersonRepository) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.relativeRepository = relativeRepository;
        this.assistedPersonRepository = assistedPersonRepository;
    }

    @PostMapping
    public ResponseEntity<Long> createInvitation(
            Principal principal,
            @RequestBody CreateInvitationResource resource) {
        Long relativeId = resource.relativeId();
        if (relativeId == null && principal != null) {
            try {
                UserId userId = new UserId(Long.valueOf(principal.getName()));
                relativeId = relativeRepository.findByUserId(userId).map(Relative::getId).orElse(1L);
            } catch (Exception e) {
                relativeId = 1L;
            }
        }
        if (relativeId == null) {
            relativeId = 1L;
        }

        Long assistedPersonId = resource.assistedPersonId();
        if (assistedPersonId == null) {
            var persons = assistedPersonRepository.findAllByRelativeId(relativeId);
            assistedPersonId = persons.isEmpty() ? 1L : persons.get(0).getId();
        }

        CreateInvitationCommand command = new CreateInvitationCommand(
                relativeId,
                assistedPersonId,
                resource.inviteeEmail()
        );
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
        Long effectiveRelativeId = relativeId;
        if (effectiveRelativeId == null && principal != null) {
            try {
                UserId userId = new UserId(Long.valueOf(principal.getName()));
                effectiveRelativeId = relativeRepository.findByUserId(userId).map(Relative::getId).orElse(1L);
            } catch (Exception e) {
                effectiveRelativeId = 1L;
            }
        }
        if (effectiveRelativeId == null) {
            effectiveRelativeId = 1L;
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
        Long effectiveRelativeId = relativeId;
        if (effectiveRelativeId == null && principal != null) {
            try {
                UserId userId = new UserId(Long.valueOf(principal.getName()));
                effectiveRelativeId = relativeRepository.findByUserId(userId).map(Relative::getId).orElse(1L);
            } catch (Exception e) {
                effectiveRelativeId = 1L;
            }
        }
        if (effectiveRelativeId == null) {
            effectiveRelativeId = 1L;
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
            List<InvitationResource> list = queryService.handle(new GetInvitationsByAssistedPersonIdQuery(assistedPersonId))
                    .stream()
                    .map(InvitationResourceAssembler::toResourceFromEntity)
                    .toList();
            return ResponseEntity.ok(list);
        }
        if (principal != null) {
            try {
                UserId userId = new UserId(Long.valueOf(principal.getName()));
                var relativeOpt = relativeRepository.findByUserId(userId);
                if (relativeOpt.isPresent()) {
                    var persons = assistedPersonRepository.findAllByRelativeId(relativeOpt.get().getId());
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
        var persons = assistedPersonRepository.findAll();
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
        List<InvitationResource> list = queryService.handle(new GetPendingInvitationsByCaregiverUserIdQuery(caregiverUserId))
                .stream()
                .map(InvitationResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }
}
