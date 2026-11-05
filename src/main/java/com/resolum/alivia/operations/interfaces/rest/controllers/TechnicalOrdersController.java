package com.resolum.alivia.operations.interfaces.rest.controllers;

import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderAccessDenied;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotFound;
import com.resolum.alivia.operations.domain.model.commands.AssignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateTechnicalOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.ReassignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.commands.RecordEvaluationResultCommand;
import com.resolum.alivia.operations.domain.model.commands.RecordInstallationResultCommand;
import com.resolum.alivia.operations.domain.model.commands.RegisterInstalledDeviceCommand;
import com.resolum.alivia.operations.domain.model.commands.RemoveInstalledDeviceCommand;
import com.resolum.alivia.operations.domain.model.commands.StartTechnicalOrderCommand;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalOrderByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalOrdersQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicianIdByUserIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicianOrdersQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.interfaces.rest.resources.AssignTechnicianResource;
import com.resolum.alivia.operations.interfaces.rest.resources.CreateTechnicalOrderResource;
import com.resolum.alivia.operations.interfaces.rest.resources.EvaluationResultResource;
import com.resolum.alivia.operations.interfaces.rest.resources.InstallationResultResource;
import com.resolum.alivia.operations.interfaces.rest.resources.ReassignTechnicianResource;
import com.resolum.alivia.operations.interfaces.rest.resources.RegisterInstalledDeviceResource;
import com.resolum.alivia.operations.interfaces.rest.resources.TechnicalOrderResource;
import com.resolum.alivia.operations.interfaces.rest.transform.TechnicalOrderResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.commands.CompleteMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.ConfirmIncidentVisitCommand;
import com.resolum.alivia.operations.domain.model.commands.EvaluateIncidentCommand;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicianScheduleQuery;
import com.resolum.alivia.operations.interfaces.rest.resources.WorkingDayResource;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentByIdQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.TestResult;
import com.resolum.alivia.operations.domain.services.IncidentQueryService;
import com.resolum.alivia.operations.interfaces.rest.resources.ResolveIncidentResource;
import org.springframework.beans.factory.annotation.Autowired;


import java.time.LocalDate;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/technical-orders", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Órdenes técnicas", description = "Disponibilidad, órdenes de evaluación e instalación y asignación de técnicos")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class TechnicalOrdersController {
    private static final java.time.format.DateTimeFormatter SHIFT_TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
    private final AppointmentCommandService commandService;
    private final AppointmentQueryService queryService;
    private final IncidentQueryService incidents;

    @Autowired
    public TechnicalOrdersController(AppointmentCommandService commandService, AppointmentQueryService queryService,
                                     IncidentQueryService incidents) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.incidents = incidents;
    }

    /** Without the incidents behind the maintenance orders. */
    public TechnicalOrdersController(AppointmentCommandService commandService, AppointmentQueryService queryService) {
        this(commandService, queryService, null);
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una orden de evaluación o instalación en un bloque",
            description = "Reserva un técnico libre del bloque (descuenta una unidad de capacidad). "
                    + "Responde 409 con alternativas si el bloque se agotó.")
    public TechnicalOrderResource create(@Valid @RequestBody CreateTechnicalOrderResource resource) {
        var order = commandService.handle(new CreateTechnicalOrderCommand(resource.draftId(), resource.serviceType(),
                SlotId.parse(resource.slotId()), resource.contactName(), resource.contactPhone(), resource.directions()));
        return toResource(order);
    }

    @GetMapping
    @Operation(summary = "Listar las órdenes técnicas, opcionalmente por tipo, fecha, estado o solicitud")
    public List<TechnicalOrderResource> list(
            @RequestParam(name = "serviceType", required = false) ServiceType serviceType,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "status", required = false) AppointmentStatus status,
            @RequestParam(name = "draftId", required = false) String draftId) {
        return queryService.handle(new GetTechnicalOrdersQuery(serviceType, date, status, draftId)).stream()
                .map(this::toResource).toList();
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Listar las órdenes asignadas al técnico que inició sesión",
            description = "Programadas y cerradas, por fecha y hora de inicio. Una cuenta que no es un técnico activo no tiene órdenes.")
    public List<TechnicalOrderResource> mine(Authentication authentication) {
        return queryService.handle(new GetTechnicianOrdersQuery(IncidentsController.userIdOf(authentication))).stream()
                .map(this::toResource).toList();
    }

    @GetMapping("/mine/schedule")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Horario laboral del técnico que inició sesión",
            description = "Los bloques de su horario asignado por día entre from y to (hasta 62 días); los días sin jornada no aparecen.")
    public List<WorkingDayResource> mySchedule(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        return queryService.handle(new GetTechnicianScheduleQuery(IncidentsController.userIdOf(authentication), from, to)).stream()
                .map(day -> new WorkingDayResource(day.date(), day.shifts().stream()
                        .map(shift -> new WorkingDayResource.Shift(shift.start().format(SHIFT_TIME), shift.end().format(SHIFT_TIME)))
                        .toList()))
                .toList();
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
    @Operation(summary = "Consultar una orden con su historial de asignaciones",
            description = "Un técnico solo consulta las órdenes que tiene asignadas; las demás responden 403.")
    public TechnicalOrderResource get(@PathVariable Long orderId, Authentication authentication) {
        requireAttendedBy(orderId, authentication);
        return queryService.handle(new GetTechnicalOrderByIdQuery(orderId))
                .map(this::toResource)
                .orElseThrow(() -> new TechnicalOrderNotFound(orderId));
    }

    @PutMapping(value = "/{orderId}/technician", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Asignar un técnico elegible a la orden programada",
            description = "El técnico debe trabajar todo el bloque y no tener otra visita en él. La orden sigue SCHEDULED.")
    public TechnicalOrderResource assign(@PathVariable Long orderId, @Valid @RequestBody AssignTechnicianResource resource) {
        var order = commandService.handle(new AssignTechnicianCommand(orderId, resource.technicianId(), resource.note()));
        return toResource(order);
    }

    @PostMapping(value = "/{orderId}/reassignment", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Reasignar la orden cuando su técnico deja de estar disponible",
            description = "Elige otro técnico elegible conservando el bloque; si nadie puede, mueve la orden al bloque "
                    + "más cercano con capacidad. Cada cambio queda en el historial.")
    public TechnicalOrderResource reassign(@PathVariable Long orderId,
                                           @Valid @RequestBody ReassignTechnicianResource resource) {
        var order = commandService.handle(new ReassignTechnicianCommand(orderId, resource.reason()));
        return toResource(order);
    }

    @PostMapping(value = "/{orderId}/evaluation-result", consumes = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
    @Operation(summary = "Cerrar la evaluación con la decisión del técnico en el hogar",
            description = "VIABLE o VIABLE_WITH_ADAPTATION (con la adaptación en notes) pasan la contratación a "
                    + "Confirmación, donde la familia revisa y confirma su configuración desde la web; al confirmar "
                    + "se abre la orden de instalación del mismo técnico. NOT_VIABLE la cierra y libera el pago "
                    + "retenido de la familia.")
    public TechnicalOrderResource recordEvaluationResult(@PathVariable Long orderId,
                                                         @Valid @RequestBody EvaluationResultResource resource,
                                                         Authentication authentication) {
        requireAttendedBy(orderId, authentication);
        var order = commandService.handle(new RecordEvaluationResultCommand(orderId, resource.result(), resource.notes()));
        return toResource(order);
    }

    @PostMapping(value = "/{orderId}/installation-result", consumes = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
    @Operation(summary = "Cerrar la orden de instalación como aprobada o rechazada",
            description = "Una instalación aprobada deja el hogar en servicio: desde entonces la familia puede reportar incidencias.")
    public TechnicalOrderResource recordInstallationResult(@PathVariable Long orderId,
                                                           @Valid @RequestBody InstallationResultResource resource,
                                                           Authentication authentication) {
        requireAttendedBy(orderId, authentication);
        var order = commandService.handle(new RecordInstallationResultCommand(orderId, resource.result(), resource.notes()));
        return toResource(order);
    }

    @PostMapping("/{orderId}/start")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
    @Operation(summary = "Iniciar la atención de la orden en el hogar",
            description = "La orden pasa a En proceso y guarda la hora exacta de inicio; repetirlo conserva la primera. "
                    + "En una orden de mantenimiento la incidencia pasa a En atención. Solo órdenes SCHEDULED; si no, 409.")
    public TechnicalOrderResource start(@PathVariable Long orderId, Authentication authentication) {
        requireAttendedBy(orderId, authentication);
        var order = commandService.handle(new StartTechnicalOrderCommand(orderId));
        return toResource(order);
    }

    @PostMapping(value = "/{orderId}/devices", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
    @Operation(summary = "Registrar un dispositivo instalado en el hogar",
            description = "Alias, dirección MAC (00:1A:2B:3C:4D:5E) y tipo. Una MAC repetida en la orden responde 409.")
    public TechnicalOrderResource registerDevice(@PathVariable Long orderId,
                                                 @Valid @RequestBody RegisterInstalledDeviceResource resource,
                                                 Authentication authentication) {
        requireAttendedBy(orderId, authentication);
        var order = commandService.handle(new RegisterInstalledDeviceCommand(orderId, resource.alias(),
                resource.macAddress(), resource.type()));
        return toResource(order);
    }

    @DeleteMapping("/{orderId}/devices/{macAddress}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
    @Operation(summary = "Quitar un dispositivo registrado por error")
    public TechnicalOrderResource removeDevice(@PathVariable Long orderId, @PathVariable String macAddress,
                                               Authentication authentication) {
        requireAttendedBy(orderId, authentication);
        var order = commandService.handle(new RemoveInstalledDeviceCommand(orderId, macAddress));
        return toResource(order);
    }

    @PostMapping(value = "/{orderId}/maintenance-result", consumes = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
    @Operation(summary = "Resolver la incidencia de una visita de mantenimiento desde la app",
            description = "Registra el diagnóstico y el trabajo realizado con la prueba superada: la orden se completa "
                    + "y la incidencia pasa a RESOLVED. Solo el técnico asignado.")
    public TechnicalOrderResource resolveIncident(@PathVariable Long orderId,
                                                  @Valid @RequestBody ResolveIncidentResource resource,
                                                  Authentication authentication) {
        requireAttendedBy(orderId, authentication);
        var order = commandService.handle(new CompleteMaintenanceOrderCommand(orderId, resource.diagnosis(),
                resource.workPerformed(), TestResult.PASSED, resource.workPerformed()));
        return toResource(order);
    }

    @PostMapping("/{orderId}/incident-evaluation")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
    @Operation(summary = "Marcar como evaluada la incidencia de la orden de mantenimiento",
            description = "Registrada → En evaluación, con su hora. Solo el técnico asignado; 409 si no está Registrada.")
    public TechnicalOrderResource evaluateIncident(@PathVariable Long orderId, Authentication authentication) {
        requireAttendedBy(orderId, authentication);
        return toResource(commandService.handle(new EvaluateIncidentCommand(orderId)));
    }

    @PostMapping("/{orderId}/visit-confirmation")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
    @Operation(summary = "Confirmar la visita de mantenimiento de la incidencia evaluada",
            description = "En evaluación → Visita programada, con su hora. Solo el técnico asignado; 409 si no está "
                    + "En evaluación.")
    public TechnicalOrderResource confirmVisit(@PathVariable Long orderId, Authentication authentication) {
        requireAttendedBy(orderId, authentication);
        return toResource(commandService.handle(new ConfirmIncidentVisitCommand(orderId)));
    }

    /** The order with the incident behind it when it is a maintenance visit. */
    private TechnicalOrderResource toResource(Appointment order) {
        var incident = incidents == null || order.getIncidentId() == null ? null
                : incidents.handle(new GetIncidentByIdQuery(order.getIncidentId())).orElse(null);
        return TechnicalOrderResourceFromEntityAssembler.toResourceFromEntity(order, incident);
    }

    /**
     * A technician only attends the orders assigned to them; the administrator attends any. Spring Security
     * always authenticates these routes, so a missing authentication only happens in standalone tests.
     */
    private void requireAttendedBy(Long orderId, Authentication authentication) {
        if (authentication == null || IncidentsController.isAdministrator(authentication)) return;
        var technicianId = queryService.handle(new GetTechnicianIdByUserIdQuery(IncidentsController.userIdOf(authentication)))
                .orElseThrow(TechnicalOrderAccessDenied::new);
        var order = queryService.handle(new GetTechnicalOrderByIdQuery(orderId))
                .orElseThrow(() -> new TechnicalOrderNotFound(orderId));
        if (!order.isAssignedTo(technicianId)) throw new TechnicalOrderAccessDenied();
    }
}
