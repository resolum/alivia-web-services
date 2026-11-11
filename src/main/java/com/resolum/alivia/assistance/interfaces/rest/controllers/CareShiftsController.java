package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.commands.AssignShiftCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeleteShiftCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetShiftsQuery;
import com.resolum.alivia.assistance.domain.services.CareShiftCommandService;
import com.resolum.alivia.assistance.domain.services.CareShiftQueryService;
import com.resolum.alivia.assistance.interfaces.rest.resources.AssignShiftResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CareShiftResource;
import com.resolum.alivia.assistance.interfaces.rest.transform.CareShiftResourceAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Care Shifts", description = "Endpoints for managing caregiver shifts")
public class CareShiftsController {

    private final CareShiftCommandService commandService;
    private final CareShiftQueryService queryService;

    public CareShiftsController(CareShiftCommandService commandService,
                               CareShiftQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    private UserId userIdOf(Principal principal) {
        if (principal == null) return null;
        try {
            return new UserId(Long.valueOf(principal.getName()));
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping({"/shifts", "/assisted-persons/{assistedPersonId}/shifts"})
    @Operation(summary = "Get caregiver shifts", description = "Retrieves caregiver shifts, optionally filtered by range")
    public ResponseEntity<List<CareShiftResource>> getShifts(
            @PathVariable(required = false) Long assistedPersonId,
            @RequestParam(required = false) Long assistedPerson,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Principal principal) {

        Long targetId = assistedPersonId != null ? assistedPersonId : assistedPerson;
        LocalDateTime fromDate = from != null && !from.isBlank() ? CareShiftResourceAssembler.parseDateTime(from) : null;
        LocalDateTime toDate = to != null && !to.isBlank() ? CareShiftResourceAssembler.parseDateTime(to) : null;

        List<CareShift> shifts = queryService.handle(new GetShiftsQuery(targetId, userIdOf(principal), fromDate, toDate));
        List<CareShiftResource> resources = shifts.stream()
                .map(CareShiftResourceAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @PostMapping({"/shifts", "/assisted-persons/{assistedPersonId}/shifts"})
    @Operation(summary = "Assign or update a caregiver shift")
    public ResponseEntity<CareShiftResource> saveShift(
            @PathVariable(required = false) Long assistedPersonId,
            @RequestBody AssignShiftResource resource,
            Principal principal) {

        Long targetId = assistedPersonId != null ? assistedPersonId : resource.assistedPersonId();
        AssignShiftCommand command = CareShiftResourceAssembler.toCommandFromResource(resource, targetId, userIdOf(principal));
        CareShift saved = commandService.handle(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(CareShiftResourceAssembler.toResourceFromEntity(saved));
    }

    @DeleteMapping("/shifts/{shiftId}")
    @Operation(summary = "Delete a caregiver shift")
    public ResponseEntity<Void> deleteShift(@PathVariable Long shiftId) {
        commandService.handle(new DeleteShiftCommand(shiftId));
        return ResponseEntity.noContent().build();
    }
}
