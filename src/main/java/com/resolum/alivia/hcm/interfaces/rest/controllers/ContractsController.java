package com.resolum.alivia.hcm.interfaces.rest.controllers;

import com.resolum.alivia.hcm.domain.model.commands.CreateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateContractCommand;
import com.resolum.alivia.hcm.domain.model.queries.GetContractQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetContractsQuery;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractViewStatus;
import com.resolum.alivia.hcm.domain.services.ContractCommandService;
import com.resolum.alivia.hcm.domain.services.ContractQueryService;
import com.resolum.alivia.hcm.interfaces.rest.resources.*;
import com.resolum.alivia.hcm.interfaces.rest.transform.ContractResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Contratos laborales", description = "Vigencia, renovación, finalización e historial contractual")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class ContractsController {
    private final ContractCommandService commandService;
    private final ContractQueryService queryService;
    private final ContractResourceAssembler assembler;
    private final Clock clock;

    public ContractsController(ContractCommandService commandService, ContractQueryService queryService,
                               ContractResourceAssembler assembler, Clock clock) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.assembler = assembler;
        this.clock = clock;
    }

    @PostMapping(value = "/employees/{employeeId}/contracts", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un contrato para un empleado existente")
    public ContractResource create(@PathVariable Long employeeId,
                                   @Valid @RequestBody CreateContractResource resource,
                                   Principal principal) {
        return assembler.toResource(commandService.handle(new CreateContractCommand(employeeId,
                resource.businessId(), resource.code(), resource.position(), resource.modality(), resource.weeklyHours(),
                resource.startDate(), resource.endDate(), resource.initialStatus(), resource.notes(),
                actorId(principal))));
    }

    @GetMapping("/contracts")
    @Operation(summary = "Listar contratos con filtros y paginación")
    public ContractPageResource list(@RequestParam Long businessId,
                                     @RequestParam(required = false) Long employeeId,
                                     @RequestParam(required = false) ContractViewStatus status,
                                     @RequestParam(required = false) ContractModality modality,
                                     @RequestParam(required = false) String search,
                                     @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        var page = queryService.handle(new GetContractsQuery(businessId, employeeId, status,
                modality, search, pageable));
        return new ContractPageResource(page.getContent().stream().map(assembler::toResource).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @GetMapping("/employees/{employeeId}/contracts")
    @Operation(summary = "Listar el historial contractual de un empleado")
    public ContractPageResource listByEmployee(@PathVariable Long employeeId,
                                                @RequestParam Long businessId,
                                                @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        var page = queryService.handle(new GetContractsQuery(businessId, employeeId, null,
                null, null, pageable));
        return new ContractPageResource(page.getContent().stream().map(assembler::toResource).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @GetMapping("/employees/{employeeId}/contracts/{contractId}")
    @Operation(summary = "Consultar el detalle de un contrato")
    public ContractResource get(@PathVariable Long employeeId, @PathVariable Long contractId,
                                @RequestParam Long businessId) {
        return assembler.toResource(queryService.handle(
                new GetContractQuery(employeeId, contractId, businessId)));
    }

    @PatchMapping(value = "/employees/{employeeId}/contracts/{contractId}",
            consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Modificar las condiciones de un contrato")
    public ContractResource update(@PathVariable Long employeeId, @PathVariable Long contractId,
                                   @RequestParam Long businessId,
                                   @Valid @RequestBody UpdateContractResource resource,
                                   Principal principal) {
        return assembler.toResource(commandService.handle(new UpdateContractCommand(employeeId,
                contractId, businessId, resource.position(), resource.modality(), resource.weeklyHours(),
                resource.startDate(), resource.endDate(), resource.notes(), actorId(principal))));
    }

    @GetMapping("/employees/{employeeId}/eligibility")
    @Operation(summary = "Consultar elegibilidad contractual del empleado")
    public EmployeeEligibilityResource eligibility(@PathVariable Long employeeId,
                                                   @RequestParam Long businessId,
                                                   @RequestParam(required = false) LocalDate date) {
        LocalDate effectiveDate = date == null ? LocalDate.now(clock) : date;
        return new EmployeeEligibilityResource(employeeId, effectiveDate,
                queryService.isEmployeeEligible(employeeId, businessId, effectiveDate));
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
