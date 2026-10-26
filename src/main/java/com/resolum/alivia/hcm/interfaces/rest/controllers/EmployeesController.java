package com.resolum.alivia.hcm.interfaces.rest.controllers;

import com.resolum.alivia.hcm.domain.model.commands.CreateEmployeeCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateEmployeeCommand;
import com.resolum.alivia.hcm.domain.model.queries.GetEmployeeByIdQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetEmployeesQuery;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeStatus;
import com.resolum.alivia.hcm.domain.services.EmployeeCommandService;
import com.resolum.alivia.hcm.domain.services.EmployeeQueryService;
import com.resolum.alivia.hcm.interfaces.rest.resources.CreateEmployeeResource;
import com.resolum.alivia.hcm.interfaces.rest.resources.EmployeePageResource;
import com.resolum.alivia.hcm.interfaces.rest.resources.EmployeeResource;
import com.resolum.alivia.hcm.interfaces.rest.resources.UpdateEmployeeResource;
import com.resolum.alivia.hcm.interfaces.rest.transform.EmployeeResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/employees", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Empleados", description = "Gestión del personal de cada negocio")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class EmployeesController {
    private final EmployeeCommandService commandService;
    private final EmployeeQueryService queryService;
    private final EmployeeResourceAssembler assembler;

    public EmployeesController(EmployeeCommandService commandService, EmployeeQueryService queryService,
                               EmployeeResourceAssembler assembler) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.assembler = assembler;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un empleado")
    public EmployeeResource createEmployee(@Valid @RequestBody CreateEmployeeResource resource) {
        return assembler.toResource(commandService.handle(new CreateEmployeeCommand(
                resource.businessId(), resource.corporateEmail(), resource.photoUrl(),
                resource.photoPublicId(), resource.phoneNumber(), resource.firstName(),
                resource.lastName(), resource.dni(), resource.birthDate(), resource.role())));
    }

    @GetMapping
    @Operation(summary = "Listar empleados con filtros y paginación")
    public EmployeePageResource getEmployees(@RequestParam Long businessId,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(required = false) EmployeeRole role,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        var page = queryService.handle(new GetEmployeesQuery(businessId, status, role, pageable));
        return new EmployeePageResource(page.getContent().stream().map(assembler::toResource).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @GetMapping("/{employeeId}")
    @Operation(summary = "Consultar un empleado")
    public EmployeeResource getEmployee(@PathVariable Long employeeId, @RequestParam Long businessId) {
        return assembler.toResource(queryService.handle(new GetEmployeeByIdQuery(employeeId, businessId)));
    }

    @PatchMapping(value = "/{employeeId}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar parcialmente un empleado")
    public EmployeeResource updateEmployee(@PathVariable Long employeeId, @RequestParam Long businessId,
                                           @Valid @RequestBody UpdateEmployeeResource resource) {
        return assembler.toResource(commandService.handle(new UpdateEmployeeCommand(
                employeeId, businessId, resource.photoUrl(), resource.photoPublicId(),
                resource.phoneNumber(), resource.firstName(), resource.lastName(),
                resource.birthDate(), resource.role())));
    }
}
