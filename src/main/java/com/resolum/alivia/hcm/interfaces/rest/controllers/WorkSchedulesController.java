package com.resolum.alivia.hcm.interfaces.rest.controllers;

import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.commands.CreateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.commands.DeactivateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkScheduleQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkSchedulesQuery;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleViewStatus;
import com.resolum.alivia.hcm.domain.services.WorkScheduleCommandService;
import com.resolum.alivia.hcm.domain.services.WorkScheduleQueryService;
import com.resolum.alivia.hcm.interfaces.rest.resources.*;
import com.resolum.alivia.hcm.interfaces.rest.transform.WorkScheduleResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Horarios laborales", description = "Jornada semanal de los empleados según su contrato vigente")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class WorkSchedulesController {
    private final WorkScheduleCommandService commandService;
    private final WorkScheduleQueryService queryService;
    private final WorkScheduleResourceAssembler assembler;

    public WorkSchedulesController(WorkScheduleCommandService commandService,
                                   WorkScheduleQueryService queryService,
                                   WorkScheduleResourceAssembler assembler) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.assembler = assembler;
    }

    @PostMapping(value = "/employees/{employeeId}/contracts/{contractId}/work-schedules",
            consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Configurar el horario laboral dentro de un contrato")
    public WorkScheduleResource create(@PathVariable Long employeeId, @PathVariable Long contractId,
                                       @Valid @RequestBody CreateWorkScheduleResource resource,
                                       Principal principal) {
        return toResource(commandService.handle(new CreateWorkScheduleCommand(employeeId, contractId,
                resource.businessId(),
                resource.name(), resource.startDate(), resource.endDate(), assembler.toShifts(resource.shifts()),
                resource.draft(), actorId(principal))));
    }

    @GetMapping("/work-schedules")
    @Operation(summary = "Listar horarios laborales con filtros y paginación")
    public WorkSchedulePageResource list(@RequestParam Long businessId,
                                         @RequestParam(required = false) Long employeeId,
                                         @RequestParam(required = false) Long contractId,
                                         @RequestParam(required = false) EmployeeRole role,
                                         @RequestParam(required = false) WorkScheduleViewStatus status,
                                         @RequestParam(required = false) String search,
                                         @PageableDefault(size = 8, sort = "id") Pageable pageable) {
        var page = queryService.handle(new GetWorkSchedulesQuery(businessId, employeeId, contractId, role, status,
                search, pageable));
        return new WorkSchedulePageResource(page.getContent().stream().map(this::toResource).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @GetMapping("/employees/{employeeId}/work-schedules/{workScheduleId}")
    @Operation(summary = "Consultar el detalle de un horario laboral")
    public WorkScheduleResource get(@PathVariable Long employeeId, @PathVariable Long workScheduleId,
                                    @RequestParam Long businessId) {
        return toResource(queryService.handle(new GetWorkScheduleQuery(employeeId, workScheduleId, businessId)));
    }

    @PatchMapping(value = "/employees/{employeeId}/work-schedules/{workScheduleId}",
            consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Modificar la vigencia y la jornada semanal de un horario")
    public WorkScheduleResource update(@PathVariable Long employeeId, @PathVariable Long workScheduleId,
                                       @RequestParam Long businessId,
                                       @Valid @RequestBody UpdateWorkScheduleResource resource,
                                       Principal principal) {
        return toResource(commandService.handle(new UpdateWorkScheduleCommand(employeeId, workScheduleId,
                businessId, resource.name(), resource.startDate(), resource.endDate(),
                assembler.toShifts(resource.shifts()), resource.draft(), actorId(principal))));
    }

    @PostMapping("/employees/{employeeId}/work-schedules/{workScheduleId}/deactivation")
    @Operation(summary = "Dar de baja un horario laboral sin eliminarlo")
    public WorkScheduleResource deactivate(@PathVariable Long employeeId, @PathVariable Long workScheduleId,
                                           @RequestParam Long businessId, Principal principal) {
        return toResource(commandService.handle(new DeactivateWorkScheduleCommand(employeeId, workScheduleId,
                businessId, actorId(principal))));
    }

    private WorkScheduleResource toResource(WorkSchedule workSchedule) {
        return assembler.toResource(workSchedule, queryService.associatedContract(workSchedule));
    }

    private static Long actorId(Principal principal) {
        if (principal == null) throw new IllegalArgumentException("Authenticated actor is required.");
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Authenticated actor identifier is invalid.", exception);
        }
    }
}
