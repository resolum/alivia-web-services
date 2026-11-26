package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assistance.domain.exceptions.InvitationNotFoundException;
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
import com.resolum.alivia.assistance.domain.exceptions.AssistanceAccessDeniedException;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.interfaces.rest.CallerIdentity;
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
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ExternalIamService externalIamService;
    private final AssistanceAccessService accessService;

    public InvitationsController(InvitationCommandService commandService,
            InvitationQueryService queryService,
            RelativeQueryService relativeQueryService,
            AssistedPersonQueryService assistedPersonQueryService,
            AssistanceAccessService accessService) {
        this(commandService, queryService, relativeQueryService, assistedPersonQueryService, accessService, null);
    }

    @Autowired
    public InvitationsController(InvitationCommandService commandService,
            InvitationQueryService queryService,
            RelativeQueryService relativeQueryService,
            AssistedPersonQueryService assistedPersonQueryService,
            AssistanceAccessService accessService,
            @Autowired(required = false) ExternalIamService externalIamService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.relativeQueryService = relativeQueryService;
        this.assistedPersonQueryService = assistedPersonQueryService;
        this.accessService = accessService;
        this.externalIamService = externalIamService;
    }

    @PostMapping
    public ResponseEntity<Long> createInvitation(
            Principal principal,
            @RequestBody CreateInvitationResource resource) {
        Long userId = CallerIdentity.requireUserId(principal);
        Long assistedPersonId = resource.assistedPersonId();
        if (assistedPersonId == null) {
            // Without a person: the first one of the relative who is calling.
            var relative = relativeQueryService.handle(new GetRelativeByUserIdQuery(userId))
                    .orElseThrow(() -> new AssistanceAccessDeniedException("Only a relative may invite a caregiver."));
            assistedPersonId = assistedPersonQueryService.handle(new GetAssistedPersonsByRelativeIdQuery(relative.getId()))
                    .stream().findFirst().map(person -> person.getId())
                    .orElseThrow(() -> new com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotFoundException(0L));
        }
        var person = accessService.requireManage(assistedPersonId, userId);

        CreateInvitationCommand command = new CreateInvitationCommand(
                person.getRelative().getId(),
                person.getId(),
                resource.inviteeEmail());
        Long id = commandService.handle(command);
        return new ResponseEntity<>(id, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<Long> acceptInvitation(
            @PathVariable Long id,
            @RequestBody(required = false) AcceptInvitationResource resource,
            Principal principal) {
        var invitation = queryService.handle(new GetInvitationByIdQuery(id))
                .orElseThrow(() -> new InvitationNotFoundException(id));
        String inviteeEmail = invitation.getInviteeEmail().email();
        Long caregiverUserId = null;
        if (principal != null) {
            try {
                caregiverUserId = Long.valueOf(principal.getName());
            } catch (NumberFormatException ignored) {
            }
        }
        if (caregiverUserId != null) {
            // A signed-in user accepts only an invitation sent to the email of their own account.
            String own = externalIamService == null ? null
                    : externalIamService.fetchEmails(java.util.Set.of(new UserId(caregiverUserId))).get(new UserId(caregiverUserId));
            if (own == null || !own.equalsIgnoreCase(inviteeEmail)) {
                throw new AssistanceAccessDeniedException("The invitation was sent to another email.");
            }
        } else if (resource != null && resource.password() != null && !resource.password().isBlank()
                && externalIamService != null) {
            // Not signed in: the invitee creates the account of the invited email with a password.
            String firstName = resource.firstName() != null && !resource.firstName().isBlank() ? resource.firstName() : "Cuidador";
            String lastName = resource.lastName() != null && !resource.lastName().isBlank() ? resource.lastName() : "Invitado";
            String phoneNumber = resource.phoneNumber() != null && !resource.phoneNumber().isBlank() ? resource.phoneNumber() : "51987654321";
            caregiverUserId = externalIamService.registerCaregiverAccount(inviteeEmail, resource.password(), firstName, lastName, phoneNumber);
        }
        if (caregiverUserId == null) {
            // The user id of the body and a default account are never used to decide who accepts.
            throw new AssistanceAccessDeniedException("Sign in, or choose a password, to accept the invitation.");
        }
        AcceptInvitationCommand command = new AcceptInvitationCommand(id, caregiverUserId);
        Long resultId = commandService.handle(command);
        return ResponseEntity.ok(resultId);
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<Long> revokeInvitation(@PathVariable Long id, Principal principal) {
        var invitation = accessService.requireManageInvitation(id, CallerIdentity.requireUserId(principal));
        RevokeInvitationCommand command = new RevokeInvitationCommand(id, invitation.getAssistedPerson().getRelative().getId());
        Long resultId = commandService.handle(command);
        return ResponseEntity.ok(resultId);
    }

    @PostMapping("/{id}/resend")
    public ResponseEntity<Long> resendInvitation(@PathVariable Long id, Principal principal) {
        var invitation = accessService.requireManageInvitation(id, CallerIdentity.requireUserId(principal));
        ResendInvitationCommand command = new ResendInvitationCommand(id, invitation.getAssistedPerson().getRelative().getId());
        Long resultId = commandService.handle(command);
        return ResponseEntity.ok(resultId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvitationResource> getInvitationById(@PathVariable Long id) {
        return queryService.handle(new GetInvitationByIdQuery(id))
                .map(invitation -> {
                    Boolean isNewUser = externalIamService != null 
                            ? !externalIamService.existsUserByEmail(invitation.getInviteeEmail().email())
                            : (invitation.getCaregiverUserId() == null);
                    return InvitationResourceAssembler.toResourceFromEntity(invitation, isNewUser);
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<InvitationResource>> getInvitations(
            Principal principal,
            @RequestParam(required = false) Long assistedPersonId) {
        Long userId = CallerIdentity.requireUserId(principal);
        if (assistedPersonId != null) {
            accessService.requireManage(assistedPersonId, userId);
            List<InvitationResource> list = queryService
                    .handle(new GetInvitationsByAssistedPersonIdQuery(assistedPersonId))
                    .stream()
                    .map(InvitationResourceAssembler::toResourceFromEntity)
                    .toList();
            return ResponseEntity.ok(list);
        }
        // Without a person: the invitations of the persons the caller is in charge of, never of anyone else.
        List<InvitationResource> all = new ArrayList<>();
        var relativeOpt = relativeQueryService.handle(new GetRelativeByUserIdQuery(userId));
        if (relativeOpt.isPresent()) {
            var persons = assistedPersonQueryService
                    .handle(new GetAssistedPersonsByRelativeIdQuery(relativeOpt.get().getId()));
            for (var person : persons) {
                all.addAll(queryService.handle(new GetInvitationsByAssistedPersonIdQuery(person.getId()))
                        .stream()
                        .map(InvitationResourceAssembler::toResourceFromEntity)
                        .toList());
            }
        }
        return ResponseEntity.ok(all);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<InvitationResource>> getPendingInvitations(
            @RequestParam(required = false) Long caregiverUserId, Principal principal) {
        // The pending invitations of an account are read by that account only.
        Long userId = CallerIdentity.requireUserId(principal);
        if (caregiverUserId != null && !caregiverUserId.equals(userId)) {
            throw new AssistanceAccessDeniedException("The invitations of another user cannot be read.");
        }
        List<InvitationResource> list = queryService
                .handle(new GetPendingInvitationsByCaregiverUserIdQuery(userId))
                .stream()
                .map(InvitationResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }
}
