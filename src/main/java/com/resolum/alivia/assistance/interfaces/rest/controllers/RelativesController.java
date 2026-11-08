package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.commands.CreateRelativeCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateRelativeCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativesByBusinessIdQuery;
import com.resolum.alivia.assistance.domain.services.RelativeCommandService;
import com.resolum.alivia.assistance.domain.services.RelativeQueryService;
import com.resolum.alivia.assistance.interfaces.rest.resources.CreateRelativeResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.RelativeResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.UpdateRelativeResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.RelativeResourceAssembler;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/relatives")
@Tag(name = "Relatives", description = "Endpoints for managing family relatives")
public class RelativesController {

    private final RelativeCommandService relativeCommandService;
    private final RelativeQueryService relativeQueryService;

    public RelativesController(RelativeCommandService relativeCommandService,
                              RelativeQueryService relativeQueryService) {
        this.relativeCommandService = relativeCommandService;
        this.relativeQueryService = relativeQueryService;
    }

    @PostMapping
    public ResponseEntity<Long> createRelative(@RequestBody CreateRelativeResource resource) {
        CreateRelativeCommand command = new CreateRelativeCommand(
                resource.businessId(),
                resource.emailAddress(),
                resource.password(),
                null, null,
                resource.phoneNumber(),
                resource.firstName(),
                resource.lastName(),
                resource.dni(),
                resource.birthDate()
        );
        Long id = relativeCommandService.handle(command);
        return new ResponseEntity<>(id, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Long> updateRelative(@PathVariable Long id, @RequestBody UpdateRelativeResource resource) {
        UpdateRelativeCommand command = new UpdateRelativeCommand(
                id,
                null, null,
                resource.phoneNumber(),
                resource.firstName(),
                resource.lastName(),
                resource.birthDate()
        );
        Long updatedId = relativeCommandService.handle(command);
        return ResponseEntity.ok(updatedId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RelativeResource> getRelativeById(@PathVariable Long id) {
        return relativeQueryService.handle(new GetRelativeByIdQuery(id))
                .map(RelativeResourceAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<RelativeResource> getRelativeByUserId(@PathVariable Long userId) {
        return relativeQueryService.handle(new GetRelativeByUserIdQuery(userId))
                .map(RelativeResourceAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<RelativeResource>> getRelativesByBusinessId(@RequestParam Long businessId) {
        List<RelativeResource> list = relativeQueryService.handle(new GetRelativesByBusinessIdQuery(businessId))
                .stream()
                .map(RelativeResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }
}
