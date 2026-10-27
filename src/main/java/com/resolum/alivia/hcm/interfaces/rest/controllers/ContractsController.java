package com.resolum.alivia.hcm.interfaces.rest.controllers;

import com.resolum.alivia.hcm.domain.model.commands.CreateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.RenewContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.TerminateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateContractCommand;
import com.resolum.alivia.hcm.domain.model.queries.GetContractQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetContractsQuery;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractViewStatus;
import com.resolum.alivia.hcm.domain.services.ContractCommandService;
import com.resolum.alivia.hcm.domain.services.ContractQueryService;
import com.resolum.alivia.hcm.interfaces.rest.DefaultBusiness;
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
import java.util.List;

import java.util.Objects;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Contratos laborales", description = "Vigencia, renovación, finalización e historial contractual")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class ContractsController {
    private final DefaultBusiness defaultBusiness;
    private final ContractCommandService commandService;
    private final ContractQueryService queryService;
    private final ContractResourceAssembler assembler;
    private final Clock clock;

    public ContractsController(ContractCommandService commandService, ContractQueryService queryService,
                               ContractResourceAssembler assembler, Clock clock,
                                   DefaultBusiness defaultBusiness) {
        this.defaultBusiness = defaultBusiness;
        this.commandService = commandService;
        this.queryService = queryService;
        this.assembler = assembler;
        this.clock = clock;
    }

    @PostMapping(value = "/employees/{employeeId}/contracts", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un contrato para un empleado existente")
    public ContractResource create(@PathVariable Long employeeId,
                                   @RequestParam(name = "businessId", required = false) Long requestedBusinessId,
                                   @Valid @RequestBody CreateContractResource resource,
                                   Principal principal) {
        var businessId = defaultBusiness.resolve(requestedBusinessId);
        return assembler.toResource(commandService.handle(new CreateContractCommand(employeeId,
                Objects.requireNonNullElse(resource.businessId(), businessId), resource.code(), resource.position(), resource.modality(), resource.weeklyHours(),
                resource.startDate(), resource.endDate(), resource.initialStatus(), resource.notes(),
                actorId(principal))));
    }

    @GetMapping("/contracts")
    @Operation(summary = "Listar contratos con filtros y paginación")
    public ContractPageResource list(@RequestParam(name = "businessId", required = false) Long requestedBusinessId,
                                     @RequestParam(required = false) Long employeeId,
                                     @RequestParam(required = false) ContractViewStatus status,
                                     @RequestParam(required = false) ContractModality modality,
                                     @RequestParam(required = false) String search,
                                     @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        var businessId = defaultBusiness.resolve(requestedBusinessId);
        var page = queryService.handle(new GetContractsQuery(businessId, employeeId, status,
                modality, search, pageable));
        return new ContractPageResource(page.getContent().stream().map(assembler::toResource).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @GetMapping("/employees/{employeeId}/contracts")
    @Operation(summary = "Listar el historial contractual de un empleado")
    public ContractPageResource listByEmployee(@PathVariable Long employeeId,
                                                @RequestParam(name = "businessId", required = false) Long requestedBusinessId,
                                                @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        var businessId = defaultBusiness.resolve(requestedBusinessId);
        var page = queryService.handle(new GetContractsQuery(businessId, employeeId, null,
                null, null, pageable));
        return new ContractPageResource(page.getContent().stream().map(assembler::toResource).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @GetMapping("/employees/{employeeId}/contracts/{contractId}")
    @Operation(summary = "Consultar el detalle de un contrato")
    public ContractResource get(@PathVariable Long employeeId, @PathVariable Long contractId,
                                @RequestParam(name = "businessId", required = false) Long requestedBusinessId) {
        var businessId = defaultBusiness.resolve(requestedBusinessId);
        return assembler.toResource(queryService.handle(
                new GetContractQuery(employeeId, contractId, businessId)));
    }

    @PatchMapping(value = "/employees/{employeeId}/contracts/{contractId}",
            consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Modificar las condiciones de un contrato")
    public ContractResource update(@PathVariable Long employeeId, @PathVariable Long contractId,
                                   @RequestParam(name = "businessId", required = false) Long requestedBusinessId,
                                   @Valid @RequestBody UpdateContractResource resource,
                                   Principal principal) {
        var businessId = defaultBusiness.resolve(requestedBusinessId);
        return assembler.toResource(commandService.handle(new UpdateContractCommand(employeeId,
                contractId, businessId, resource.position(), resource.modality(), resource.weeklyHours(),
                resource.startDate(), resource.endDate(), resource.notes(), actorId(principal))));
    }

    @PostMapping(value = "/employees/{employeeId}/contracts/{contractId}/renewals",
            consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Renovar un contrato")
    public ContractResource renew(@PathVariable Long employeeId, @PathVariable Long contractId,
                                  @RequestParam(name = "businessId", required = false) Long requestedBusinessId,
                                  @Valid @RequestBody RenewContractResource resource,
                                  Principal principal) {
        var businessId = defaultBusiness.resolve(requestedBusinessId);
        return assembler.toResource(commandService.handle(new RenewContractCommand(employeeId,
                contractId, businessId, resource.startDate(), resource.endDate(), actorId(principal))));
    }

    @PostMapping("/employees/{employeeId}/contracts/{contractId}/termination")
    @Operation(summary = "Finalizar un contrato sin eliminarlo")
    public ContractResource terminate(@PathVariable Long employeeId, @PathVariable Long contractId,
                                      @RequestParam(name = "businessId", required = false) Long requestedBusinessId, Principal principal) {
        var businessId = defaultBusiness.resolve(requestedBusinessId);
        return assembler.toResource(commandService.handle(new TerminateContractCommand(employeeId,
                contractId, businessId, actorId(principal))));
    }

    @GetMapping("/employees/{employeeId}/contracts/{contractId}/history")
    @Operation(summary = "Consultar las versiones y actores de un contrato")
    public List<ContractVersionResource> history(@PathVariable Long employeeId,
                                                 @PathVariable Long contractId,
                                                 @RequestParam(name = "businessId", required = false) Long requestedBusinessId) {
        var businessId = defaultBusiness.resolve(requestedBusinessId);
        return queryService.history(new GetContractQuery(employeeId, contractId, businessId)).stream()
                .map(assembler::toHistoryResource).toList();
    }

    @GetMapping("/employees/{employeeId}/eligibility")
    @Operation(summary = "Consultar elegibilidad contractual del empleado")
    public EmployeeEligibilityResource eligibility(@PathVariable Long employeeId,
                                                   @RequestParam(name = "businessId", required = false) Long requestedBusinessId,
                                                   @RequestParam(required = false) LocalDate date) {
        var businessId = defaultBusiness.resolve(requestedBusinessId);
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
