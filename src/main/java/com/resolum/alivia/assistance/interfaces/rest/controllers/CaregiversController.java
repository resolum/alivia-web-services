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

    public CaregiversController(CaregiverCommandService commandService,
                                CaregiverQueryService queryService,
                                RelativeQueryService relativeQueryService,
                                AssistedPersonQueryService assistedPersonQueryService) {
        this(commandService, queryService, relativeQueryService, assistedPersonQueryService, null, null);
    }

    @Autowired
    public CaregiversController(CaregiverCommandService commandService,
                                CaregiverQueryService queryService,
                                RelativeQueryService relativeQueryService,
                                AssistedPersonQueryService assistedPersonQueryService,
                                @Autowired(required = false) ExternalIamService externalIamService,
                                @Autowired(required = false) ExternalProfilesService externalProfilesService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.relativeQueryService = relativeQueryService;
        this.assistedPersonQueryService = assistedPersonQueryService;
        this.externalIamService = externalIamService;
        this.externalProfilesService = externalProfilesService;
    }

    @PostMapping
    public ResponseEntity<Long> createCaregiver(@RequestBody CreateCaregiverResource resource) {
        CreateCaregiverCommand command = new CreateCaregiverCommand(
                resource.assistedPersonId(),
                resource.caregiverUserId()
        );
        Long id = commandService.handle(command);
        return new ResponseEntity<>(id, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<Long> revokeCaregiver(@PathVariable Long id,
                                                @RequestParam(required = false) Long relativeId,
                                                Principal principal) {
        Long effectiveRelativeId = relativeId;
        if (effectiveRelativeId == null && principal != null) {
            try {
                Long userId = Long.valueOf(principal.getName());
                effectiveRelativeId = relativeQueryService.handle(new GetRelativeByUserIdQuery(userId)).map(Relative::getId).orElse(1L);
            } catch (Exception e) {
                effectiveRelativeId = 1L;
            }
        }
        if (effectiveRelativeId == null) {
            effectiveRelativeId = 1L;
        }
        RevokeCaregiverAccessCommand command = new RevokeCaregiverAccessCommand(id, effectiveRelativeId);
        Long resultId = commandService.handle(command);
        return ResponseEntity.ok(resultId);
    }

    @GetMapping
    public ResponseEntity<List<CaregiverResource>> getCaregivers(
            Principal principal,
            @RequestParam(required = false) Long assistedPersonId) {
        if (assistedPersonId != null) {
            List<Caregiver> entities = queryService.handle(new GetCaregiversByAssistedPersonIdQuery(assistedPersonId));
            return ResponseEntity.ok(enrichCaregivers(entities));
        }
        if (principal != null) {
            try {
                Long userId = Long.valueOf(principal.getName());
                var relativeOpt = relativeQueryService.handle(new GetRelativeByUserIdQuery(userId));
                if (relativeOpt.isPresent()) {
                    var persons = assistedPersonQueryService.handle(new GetAssistedPersonsByRelativeIdQuery(relativeOpt.get().getId()));
                    List<Caregiver> all = new ArrayList<>();
                    for (var person : persons) {
                        all.addAll(queryService.handle(new GetCaregiversByAssistedPersonIdQuery(person.getId())));
                    }
                    return ResponseEntity.ok(enrichCaregivers(all));
                }
                List<Caregiver> list = queryService.handle(new GetCaregiversByUserIdQuery(userId));
                if (!list.isEmpty()) {
                    return ResponseEntity.ok(enrichCaregivers(list));
                }
            } catch (Exception e) {
                log.warn("Could not find caregivers by principal {}: {}", principal.getName(), e.getMessage());
            }
        }
        var persons = assistedPersonQueryService.handle(new GetAllAssistedPersonsQuery());
        List<Caregiver> all = new ArrayList<>();
        for (var person : persons) {
            all.addAll(queryService.handle(new GetCaregiversByAssistedPersonIdQuery(person.getId())));
        }
        return ResponseEntity.ok(enrichCaregivers(all));
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
    public ResponseEntity<CaregiverResource> getCaregiverById(@PathVariable Long id) {
        return queryService.handle(new GetCaregiversByUserIdQuery(id))
                .stream()
                .findFirst()
                .map(CaregiverResourceAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CaregiverResource>> getCaregiversByUserId(@PathVariable Long userId) {
        List<CaregiverResource> list = queryService.handle(new GetCaregiversByUserIdQuery(userId))
                .stream()
                .map(CaregiverResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }
}
