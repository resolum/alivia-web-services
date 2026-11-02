package com.resolum.alivia.operations.interfaces.rest.controllers;

import com.resolum.alivia.operations.domain.model.commands.CompleteMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.interfaces.rest.resources.CompleteMaintenanceOrderResource;
import com.resolum.alivia.operations.interfaces.rest.resources.CreateMaintenanceOrderResource;
import com.resolum.alivia.operations.interfaces.rest.resources.TechnicalOrderResource;
import com.resolum.alivia.operations.interfaces.rest.transform.TechnicalOrderResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/maintenance-orders", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Soporte técnico", description = "Incidencias de los hogares en servicio y sus visitas de mantenimiento")
public class MaintenanceOrdersController {
    private final AppointmentCommandService commandService;

    public MaintenanceOrdersController(AppointmentCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Programar la visita de mantenimiento de una incidencia",
            description = "Reserva un técnico del bloque (capacidad técnica) y deja la orden SCHEDULED; "
                    + "la incidencia pasa a SCHEDULED. Responde 409 con alternativas si el bloque se agotó.")
    public TechnicalOrderResource create(@Valid @RequestBody CreateMaintenanceOrderResource resource,
                                         Authentication authentication) {
        var order = commandService.handle(new CreateMaintenanceOrderCommand(resource.incidentId(),
                SlotId.parse(resource.slotId()), IncidentsController.userIdOf(authentication),
                IncidentsController.isAdministrator(authentication)));
        return TechnicalOrderResourceFromEntityAssembler.toResourceFromEntity(order);
    }

    @PostMapping(value = "/{orderId}/completion", consumes = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
    @Operation(summary = "Registrar diagnóstico, mantenimiento, prueba y resolución",
            description = "Sin resultado de prueba responde 400 y la orden sigue abierta. Si la prueba pasa, la "
                    + "incidencia queda RESOLVED; si falla, vuelve a OPEN para programar otra visita.")
    public TechnicalOrderResource complete(@PathVariable Long orderId,
                                           @Valid @RequestBody CompleteMaintenanceOrderResource resource) {
        var order = commandService.handle(new CompleteMaintenanceOrderCommand(orderId, resource.diagnosis(),
                resource.maintenancePerformed(), resource.testResult(), resource.resolution()));
        return TechnicalOrderResourceFromEntityAssembler.toResourceFromEntity(order);
    }
}
