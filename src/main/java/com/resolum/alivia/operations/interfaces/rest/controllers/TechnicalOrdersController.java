package com.resolum.alivia.operations.interfaces.rest.controllers;

import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotFound;
import com.resolum.alivia.operations.domain.model.commands.AssignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateTechnicalOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.ReassignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.commands.RecordInstallationResultCommand;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalOrderByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalOrdersQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.interfaces.rest.resources.AssignTechnicianResource;
import com.resolum.alivia.operations.interfaces.rest.resources.CreateTechnicalOrderResource;
import com.resolum.alivia.operations.interfaces.rest.resources.InstallationResultResource;
import com.resolum.alivia.operations.interfaces.rest.resources.ReassignTechnicianResource;
import com.resolum.alivia.operations.interfaces.rest.resources.TechnicalOrderResource;
import com.resolum.alivia.operations.interfaces.rest.transform.TechnicalOrderResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/technical-orders", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Órdenes técnicas", description = "Disponibilidad, órdenes de evaluación e instalación y asignación de técnicos")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class TechnicalOrdersController {
    private final AppointmentCommandService commandService;
    private final AppointmentQueryService queryService;

    public TechnicalOrdersController(AppointmentCommandService commandService, AppointmentQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una orden de evaluación o instalación en un bloque",
            description = "Reserva un técnico libre del bloque (descuenta una unidad de capacidad). "
                    + "Responde 409 con alternativas si el bloque se agotó.")
    public TechnicalOrderResource create(@Valid @RequestBody CreateTechnicalOrderResource resource) {
        var order = commandService.handle(new CreateTechnicalOrderCommand(resource.draftId(), resource.serviceType(),
                SlotId.parse(resource.slotId()), resource.contactName(), resource.contactPhone(), resource.directions()));
        return TechnicalOrderResourceFromEntityAssembler.toResourceFromEntity(order);
    }

    @GetMapping
    @Operation(summary = "Listar las órdenes técnicas, opcionalmente por tipo, fecha, estado o solicitud")
    public List<TechnicalOrderResource> list(
            @RequestParam(name = "serviceType", required = false) ServiceType serviceType,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "status", required = false) AppointmentStatus status,
            @RequestParam(name = "draftId", required = false) String draftId) {
        return queryService.handle(new GetTechnicalOrdersQuery(serviceType, date, status, draftId)).stream()
                .map(TechnicalOrderResourceFromEntityAssembler::toResourceFromEntity).toList();
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Consultar una orden con su historial de asignaciones")
    public TechnicalOrderResource get(@PathVariable Long orderId) {
        return queryService.handle(new GetTechnicalOrderByIdQuery(orderId))
                .map(TechnicalOrderResourceFromEntityAssembler::toResourceFromEntity)
                .orElseThrow(() -> new TechnicalOrderNotFound(orderId));
    }

    @PutMapping(value = "/{orderId}/technician", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Asignar un técnico elegible a la orden programada",
            description = "El técnico debe trabajar todo el bloque y no tener otra visita en él. La orden sigue SCHEDULED.")
    public TechnicalOrderResource assign(@PathVariable Long orderId, @Valid @RequestBody AssignTechnicianResource resource) {
        var order = commandService.handle(new AssignTechnicianCommand(orderId, resource.technicianId(), resource.note()));
        return TechnicalOrderResourceFromEntityAssembler.toResourceFromEntity(order);
    }

    @PostMapping(value = "/{orderId}/reassignment", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Reasignar la orden cuando su técnico deja de estar disponible",
            description = "Elige otro técnico elegible conservando el bloque; si nadie puede, mueve la orden al bloque "
                    + "más cercano con capacidad. Cada cambio queda en el historial.")
    public TechnicalOrderResource reassign(@PathVariable Long orderId,
                                           @Valid @RequestBody ReassignTechnicianResource resource) {
        var order = commandService.handle(new ReassignTechnicianCommand(orderId, resource.reason()));
        return TechnicalOrderResourceFromEntityAssembler.toResourceFromEntity(order);
    }

    @PostMapping(value = "/{orderId}/installation-result", consumes = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
    @Operation(summary = "Cerrar la orden de instalación como aprobada o rechazada",
            description = "Una instalación aprobada deja el hogar en servicio: desde entonces la familia puede reportar incidencias.")
    public TechnicalOrderResource recordInstallationResult(@PathVariable Long orderId,
                                                           @Valid @RequestBody InstallationResultResource resource) {
        var order = commandService.handle(new RecordInstallationResultCommand(orderId, resource.result(), resource.notes()));
        return TechnicalOrderResourceFromEntityAssembler.toResourceFromEntity(order);
    }
}
