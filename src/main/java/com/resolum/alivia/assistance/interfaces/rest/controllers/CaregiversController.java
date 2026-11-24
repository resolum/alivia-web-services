package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.CreateCaregiverCommand;
import com.resolum.alivia.assistance.domain.model.commands.RevokeCaregiverAccessCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetAllAssistedPersonsQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByRelativeIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCaregiversByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCaregiversByUserIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.domain.exceptions.AssistanceAccessDeniedException;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.interfaces.rest.CallerIdentity;
import com.resolum.alivia.assistance.domain.services.CaregiverCommandService;
import com.resolum.alivia.assistance.domain.services.CaregiverQueryService;
import com.resolum.alivia.assistance.domain.services.RelativeQueryService;
import com.resolum.alivia.assistance.interfaces.rest.resources.CaregiverResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CreateCaregiverResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.CaregiverResourceAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/caregivers")
@Tag(name = "Caregivers", description = "Endpoints for caregivers")
public class CaregiversController {

    private final CaregiverCommandService commandService;
    private final CaregiverQueryService queryService;
    private final RelativeQueryService relativeQueryService;
    private final AssistedPersonQueryService assistedPersonQueryService;
    private final ExternalIamService externalIamService;
    private final ExternalProfilesService externalProfilesService;
    private final AssistanceAccessService accessService;

    public CaregiversController(CaregiverCommandService commandService,
                                CaregiverQueryService queryService,
                                RelativeQueryService relativeQueryService,
                                AssistedPersonQueryService assistedPersonQueryService,
                                AssistanceAccessService accessService) {
        this(commandService, queryService, relativeQueryService, assistedPersonQueryService, accessService, null, null);
    }

    @Autowired
    public CaregiversController(CaregiverCommandService commandService,
                                CaregiverQueryService queryService,
                                RelativeQueryService relativeQueryService,
                                AssistedPersonQueryService assistedPersonQueryService,
                                AssistanceAccessService accessService,
                                @Autowired(required = false) ExternalIamService externalIamService,
                                @Autowired(required = false) ExternalProfilesService externalProfilesService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.relativeQueryService = relativeQueryService;
        this.assistedPersonQueryService = assistedPersonQueryService;
        this.externalIamService = externalIamService;
        this.externalProfilesService = externalProfilesService;
        this.accessService = accessService;
    }

    @PostMapping
    public ResponseEntity<Long> createCaregiver(@RequestBody CreateCaregiverResource resource, Principal principal) {
        accessService.requireManage(resource.assistedPersonId(), CallerIdentity.requireUserId(principal));
        CreateCaregiverCommand command = new CreateCaregiverCommand(
                resource.assistedPersonId(),
                resource.caregiverUserId()
        );
        Long id = commandService.handle(command);
        return new ResponseEntity<>(id, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<Long> revokeCaregiver(@PathVariable Long id, Principal principal) {
        var caregiver = accessService.requireManageCaregiver(id, CallerIdentity.requireUserId(principal));
        // Revoked on behalf of the relative in charge of the person; never a relative id sent by the client.
        RevokeCaregiverAccessCommand command = new RevokeCaregiverAccessCommand(
                id, caregiver.getAssistedPerson().getRelative().getId());
        Long resultId = commandService.handle(command);
        return ResponseEntity.ok(resultId);
    }

    @GetMapping
    public ResponseEntity<List<CaregiverResource>> getCaregivers(
            Principal principal,
            @RequestParam(required = false) Long assistedPersonId) {
        Long userId = CallerIdentity.requireUserId(principal);
        if (assistedPersonId != null) {
            accessService.requireRead(assistedPersonId, userId);
            List<Caregiver> entities = queryService.handle(new GetCaregiversByAssistedPersonIdQuery(assistedPersonId));
            return ResponseEntity.ok(enrichCaregivers(entities));
        }
        // Without a person: the caregivers of the persons the caller is in charge of or, for a caregiver, the
        // caller's own records. Never the caregivers of persons the caller does not take care of.
        var relativeOpt = relativeQueryService.handle(new GetRelativeByUserIdQuery(userId));
        if (relativeOpt.isPresent()) {
            var persons = assistedPersonQueryService.handle(new GetAssistedPersonsByRelativeIdQuery(relativeOpt.get().getId()));
            List<Caregiver> all = new ArrayList<>();
            for (var person : persons) {
                all.addAll(queryService.handle(new GetCaregiversByAssistedPersonIdQuery(person.getId())));
            }
            return ResponseEntity.ok(enrichCaregivers(all));
        }
        return ResponseEntity.ok(enrichCaregivers(queryService.handle(new GetCaregiversByUserIdQuery(userId))));
    }

    private List<CaregiverResource> enrichCaregivers(List<Caregiver> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        if (externalIamService == null) {
            return entities.stream().map(CaregiverResourceAssembler::toResourceFromEntity).toList();
        }
        Set<UserId> userIds = entities.stream()
                .map(Caregiver::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UserId, String> emails = externalIamService.fetchEmails(userIds);
        Map<UserId, PersonProfileId> profileIds = externalIamService.fetchPersonProfileIds(userIds);

        return entities.stream().map(c -> {
            CaregiverResource base = CaregiverResourceAssembler.toResourceFromEntity(c);
            UserId uid = c.getUserId();
            String email = uid != null ? emails.get(uid) : null;
            String fullName = null;
            String phoneNumber = null;
            String photoUrl = null;
            if (uid != null && externalProfilesService != null && profileIds.containsKey(uid)) {
                var profileOpt = externalProfilesService.fetchPersonProfile(profileIds.get(uid));
                if (profileOpt.isPresent()) {
                    var profile = profileOpt.get();
                    String first = profile.firstName() != null ? profile.firstName().trim() : "";
                    String last = profile.lastName() != null ? profile.lastName().trim() : "";
                    fullName = (first + " " + last).trim();
                    if (fullName.isEmpty()) fullName = null;
                    phoneNumber = profile.phoneNumber();
                    photoUrl = profile.photoUrl();
                    if (email == null || email.isBlank()) {
                        email = profile.emailAddress();
                    }
                }
            }
            return new CaregiverResource(
                    base.id(),
                    base.userId(),
                    base.assistedPersonId(),
                    base.status(),
                    fullName,
                    email,
                    phoneNumber,
                    photoUrl
            );
        }).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaregiverResource> getCaregiverById(@PathVariable Long id, Principal principal) {
        var caregiver = accessService.requireReadCaregiver(id, CallerIdentity.requireUserId(principal));
        return ResponseEntity.ok(CaregiverResourceAssembler.toResourceFromEntity(caregiver));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CaregiverResource>> getCaregiversByUserId(@PathVariable Long userId, Principal principal) {
        // The caregiver records of an account are read by that account only.
        if (!CallerIdentity.requireUserId(principal).equals(userId)) {
            throw new AssistanceAccessDeniedException("The caregiver records of another user cannot be read.");
        }
        List<CaregiverResource> list = queryService.handle(new GetCaregiversByUserIdQuery(userId))
                .stream()
                .map(CaregiverResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }
}
