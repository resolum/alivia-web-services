package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.commands.CreateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetAllAssistedPersonsQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByRelativeIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByUserIdQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.Kinship;
import com.resolum.alivia.assistance.domain.model.valueobjects.MobilityLevel;
import com.resolum.alivia.assistance.domain.services.AssistedPersonCommandService;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.interfaces.rest.resources.AssistedPersonResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CreateAssistedPersonResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.UpdateAssistedPersonResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.AssistedPersonResourceAssembler;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
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

    public AssistedPersonsController(AssistedPersonCommandService commandService,
                                   AssistedPersonQueryService queryService,
                                   ExternalProfilesService externalProfilesService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.externalProfilesService = externalProfilesService;
    }

    private AssistedPersonResource toResource(AssistedPerson entity) {
        ProfilesContextFacade.PersonProfileData profileData = null;
        if (entity.getPersonProfileId() != null) {
            profileData = externalProfilesService.fetchPersonProfile(entity.getPersonProfileId()).orElse(null);
        }
        return AssistedPersonResourceAssembler.toResourceFromEntity(entity, profileData);
    }

    private UserId userIdOf(Principal principal) {
        if (principal == null) return null;
        try {
            return new UserId(Long.valueOf(principal.getName()));
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping
    public ResponseEntity<AssistedPersonResource> createAssistedPerson(
            @RequestBody CreateAssistedPersonResource resource,
            Principal principal) {
        UserId userId = userIdOf(principal);
        CreateAssistedPersonCommand command = new CreateAssistedPersonCommand(
                resource.relativeId(),
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
                userId != null ? userId.userId() : null
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
            @RequestBody UpdateAssistedPersonResource resource) {
        UpdateAssistedPersonCommand command = new UpdateAssistedPersonCommand(
                id,
                resource.relativeId(),
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
    public ResponseEntity<AssistedPersonResource> getAssistedPersonById(@PathVariable Long id) {
        return queryService.handle(new GetAssistedPersonByIdQuery(id))
                .map(this::toResource)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<AssistedPersonResource>> getAssistedPersons(
            Principal principal,
            @RequestParam(required = false) Long relativeId) {
        if (relativeId != null) {
            List<AssistedPersonResource> list = queryService.handle(new GetAssistedPersonsByRelativeIdQuery(relativeId))
                    .stream()
                    .map(this::toResource)
                    .toList();
            return ResponseEntity.ok(list);
        }
        if (principal != null) {
            UserId userId = userIdOf(principal);
            if (userId != null) {
                List<AssistedPersonResource> list = queryService.handle(new GetAssistedPersonsByUserIdQuery(userId))
                        .stream()
                        .map(this::toResource)
                        .toList();
                if (!list.isEmpty()) {
                    return ResponseEntity.ok(list);
                }
            }
        }
        List<AssistedPersonResource> list = queryService.handle(new GetAllAssistedPersonsQuery())
                .stream()
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
