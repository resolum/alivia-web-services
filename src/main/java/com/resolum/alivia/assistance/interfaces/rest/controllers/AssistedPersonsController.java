package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.commands.CreateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.exceptions.AssistanceAccessDeniedException;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.Kinship;
import com.resolum.alivia.assistance.domain.model.valueobjects.MobilityLevel;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.domain.services.AssistedPersonCommandService;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.domain.services.RelativeQueryService;
import com.resolum.alivia.assistance.interfaces.rest.CallerIdentity;
import com.resolum.alivia.assistance.interfaces.rest.resources.AssistedPersonResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CreateAssistedPersonResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.UpdateAssistedPersonResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.AssistedPersonResourceAssembler;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/assisted-persons")
@Tag(name = "Assisted Persons", description = "Endpoints for managing assisted persons")
public class AssistedPersonsController {

    private final AssistedPersonCommandService commandService;
    private final AssistedPersonQueryService queryService;
    private final ExternalProfilesService externalProfilesService;
    private final AssistanceAccessService accessService;
    private final RelativeQueryService relativeQueryService;

    public AssistedPersonsController(AssistedPersonCommandService commandService,
                                   AssistedPersonQueryService queryService,
                                   ExternalProfilesService externalProfilesService,
                                   AssistanceAccessService accessService,
                                   RelativeQueryService relativeQueryService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.externalProfilesService = externalProfilesService;
        this.accessService = accessService;
        this.relativeQueryService = relativeQueryService;
    }

    private AssistedPersonResource toResource(AssistedPerson entity) {
        ProfilesContextFacade.PersonProfileData profileData = null;
        if (entity.getPersonProfileId() != null) {
            profileData = externalProfilesService.fetchPersonProfile(entity.getPersonProfileId()).orElse(null);
        }
        return AssistedPersonResourceAssembler.toResourceFromEntity(entity, profileData);
    }

    @PostMapping
    public ResponseEntity<AssistedPersonResource> createAssistedPerson(
            @RequestBody CreateAssistedPersonResource resource,
            Principal principal) {
        Long userId = CallerIdentity.requireUserId(principal);
        // Only a relative registers an assisted person, and always as itself: the relative id of the body is
        // never trusted to say whose person it is.
        var relative = relativeQueryService.handle(new GetRelativeByUserIdQuery(userId))
                .orElseThrow(() -> new AssistanceAccessDeniedException("Only a relative may register an assisted person."));
        if (resource.relativeId() != null && !resource.relativeId().equals(relative.getId())) {
            throw new AssistanceAccessDeniedException("The relative of the request is not the caller.");
        }
        if (resource.householdId() != null && !accessService.canManageHousehold(resource.householdId(), userId)) {
            throw new AssistanceAccessDeniedException("The caller may not manage household " + resource.householdId() + ".");
        }
        CreateAssistedPersonCommand command = new CreateAssistedPersonCommand(
                relative.getId(),
                resource.emailAddress(),
                null, null,
                resource.phoneNumber(),
                resource.firstName(),
                resource.lastName(),
                resource.effectiveDni(),
                resource.birthDate(),
                resource.householdId(),
                parseKinship(resource.kinship()),
                parseMobilityLevel(resource.mobilityLevel()),
                resource.emergencyContactName(),
                resource.emergencyContactPhone(),
                resource.consentGiven() != null ? resource.consentGiven() : true,
                userId
        );
        Long id = commandService.handle(command);
        return queryService.handle(new GetAssistedPersonByIdQuery(id))
                .map(this::toResource)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r))
                .orElse(ResponseEntity.status(HttpStatus.CREATED).body(new AssistedPersonResource(id, null, resource.relativeId())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Long> updateAssistedPerson(
            @PathVariable Long id,
            Principal principal,
            @RequestBody UpdateAssistedPersonResource resource) {
        Long userId = CallerIdentity.requireUserId(principal);
        var person = accessService.requireManage(id, userId);
        if (resource.householdId() != null && !resource.householdId().equals(person.getHouseholdId())
                && !accessService.canManageHousehold(resource.householdId(), userId)) {
            throw new AssistanceAccessDeniedException("The caller may not manage household " + resource.householdId() + ".");
        }
        UpdateAssistedPersonCommand command = new UpdateAssistedPersonCommand(
                id,
                person.getRelative().getId(),
                null, null,
                resource.phoneNumber(),
                resource.firstName(),
                resource.lastName(),
                resource.birthDate(),
                resource.householdId(),
                parseKinship(resource.kinship()),
                parseMobilityLevel(resource.mobilityLevel()),
                resource.emergencyContactName(),
                resource.emergencyContactPhone()
        );
        Long updatedId = commandService.handle(command);
        return ResponseEntity.ok(updatedId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssistedPersonResource> getAssistedPersonById(@PathVariable Long id, Principal principal) {
        var person = accessService.requireRead(id, CallerIdentity.requireUserId(principal));
        return ResponseEntity.ok(toResource(person));
    }

    @GetMapping
    public ResponseEntity<List<AssistedPersonResource>> getAssistedPersons(
            Principal principal,
            @RequestParam(required = false) Long relativeId) {
        // Only the persons the caller takes care of; a relativeId can narrow the list, never widen it.
        List<AssistedPersonResource> list = accessService.readableBy(CallerIdentity.requireUserId(principal)).stream()
                .filter(person -> relativeId == null
                        || (person.getRelative() != null && relativeId.equals(person.getRelative().getId())))
                .map(this::toResource)
                .toList();
        return ResponseEntity.ok(list);
    }

    private Kinship parseKinship(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Kinship.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return switch (value.trim().toUpperCase()) {
                case "PADRE", "MADRE" -> Kinship.PARENT;
                case "ABUELO", "ABUELA" -> Kinship.GRANDPARENT;
                case "TIO", "TIA" -> Kinship.AUNT_UNCLE;
                case "CONYUGE", "ESPOSO", "ESPOSA" -> Kinship.SPOUSE;
                case "HERMANO", "HERMANA" -> Kinship.SIBLING;
                default -> Kinship.OTHER;
            };
        }
    }

    private MobilityLevel parseMobilityLevel(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return MobilityLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MobilityLevel.PARTIAL;
        }
    }
}
