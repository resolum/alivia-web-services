package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.commands.CreateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByRelativeIdQuery;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.AssistedPersonCommandService;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.interfaces.rest.resources.AssistedPersonResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CreateAssistedPersonResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.UpdateAssistedPersonResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.AssistedPersonResourceAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/v1/assisted-persons")
@Tag(name = "Assisted Persons", description = "Endpoints for managing assisted persons")
public class AssistedPersonsController {

    private final AssistedPersonCommandService commandService;
    private final AssistedPersonQueryService queryService;
    private final AssistedPersonRepository assistedPersonRepository;
    private final RelativeRepository relativeRepository;
    private final CaregiverRepository caregiverRepository;

    public AssistedPersonsController(AssistedPersonCommandService commandService,
                                   AssistedPersonQueryService queryService,
                                   AssistedPersonRepository assistedPersonRepository,
                                   RelativeRepository relativeRepository,
                                   CaregiverRepository caregiverRepository) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.assistedPersonRepository = assistedPersonRepository;
        this.relativeRepository = relativeRepository;
        this.caregiverRepository = caregiverRepository;
    }

    @PostMapping
    public ResponseEntity<Long> createAssistedPerson(@RequestBody CreateAssistedPersonResource resource) {
        CreateAssistedPersonCommand command = new CreateAssistedPersonCommand(
                resource.relativeId(),
                resource.emailAddress(),
                null, null,
                resource.phoneNumber(),
                resource.firstName(),
                resource.lastName(),
                resource.dni(),
                resource.birthDate()
        );
        Long id = commandService.handle(command);
        return new ResponseEntity<>(id, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Long> updateAssistedPerson(@PathVariable Long id, @RequestBody UpdateAssistedPersonResource resource) {
        UpdateAssistedPersonCommand command = new UpdateAssistedPersonCommand(
                id,
                resource.relativeId(),
                null, null,
                resource.phoneNumber(),
                resource.firstName(),
                resource.lastName(),
                resource.birthDate()
        );
        Long updatedId = commandService.handle(command);
        return ResponseEntity.ok(updatedId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssistedPersonResource> getAssistedPersonById(@PathVariable Long id) {
        return queryService.handle(new GetAssistedPersonByIdQuery(id))
                .map(AssistedPersonResourceAssembler::toResourceFromEntity)
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
                    .map(AssistedPersonResourceAssembler::toResourceFromEntity)
                    .toList();
            return ResponseEntity.ok(list);
        }
        if (principal != null) {
            try {
                UserId userId = new UserId(Long.valueOf(principal.getName()));
                var relativeOpt = relativeRepository.findByUserId(userId);
                if (relativeOpt.isPresent()) {
                    List<AssistedPersonResource> list = queryService.handle(new GetAssistedPersonsByRelativeIdQuery(relativeOpt.get().getId()))
                            .stream()
                            .map(AssistedPersonResourceAssembler::toResourceFromEntity)
                            .toList();
                    return ResponseEntity.ok(list);
                }
                var caregivers = caregiverRepository.findAllByUserId(userId);
                List<AssistedPersonResource> list = caregivers.stream()
                        .map(Caregiver::getAssistedPerson)
                        .filter(Objects::nonNull)
                        .distinct()
                        .map(AssistedPersonResourceAssembler::toResourceFromEntity)
                        .toList();
                if (!list.isEmpty()) {
                    return ResponseEntity.ok(list);
                }
            } catch (Exception e) {
                log.warn("Could not find assisted persons by principal {}: {}", principal.getName(), e.getMessage());
            }
        }
        List<AssistedPersonResource> list = assistedPersonRepository.findAll()
                .stream()
                .map(AssistedPersonResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }
}
