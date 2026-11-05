package com.resolum.alivia.operations.interfaces.rest.controllers;

import com.resolum.alivia.operations.domain.exceptions.IncidentNotFound;
import com.resolum.alivia.operations.domain.model.commands.AttachEvidenceCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateIncidentCommand;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetMaintenanceOrdersByIncidentIdsQuery;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentsQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatus;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.domain.services.IncidentCommandService;
import com.resolum.alivia.operations.domain.services.IncidentQueryService;
import com.resolum.alivia.operations.interfaces.rest.resources.AttachEvidenceResource;
import com.resolum.alivia.operations.interfaces.rest.resources.CreateIncidentResource;
import com.resolum.alivia.operations.interfaces.rest.resources.IncidentResource;
import com.resolum.alivia.operations.interfaces.rest.transform.IncidentResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.resolum.alivia.operations.domain.model.commands.ReportIncidentCommand;
import com.resolum.alivia.operations.domain.model.queries.GetReportableDevicesQuery;
import com.resolum.alivia.operations.interfaces.rest.resources.ReportIncidentResource;
import com.resolum.alivia.operations.interfaces.rest.resources.ReportableDeviceResource;
import com.resolum.alivia.operations.interfaces.rest.resources.ReportableHomeResource;
import org.springframework.http.ResponseEntity;


import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

import com.resolum.alivia.operations.application.internal.outboundservices.storage.IncidentPhotoStorage;
import com.resolum.alivia.operations.domain.exceptions.IncidentAccessDenied;
import com.resolum.alivia.operations.domain.exceptions.PhotoUploadUnavailable;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/support-incidents", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Soporte técnico", description = "Incidencias de los hogares en servicio y sus visitas de mantenimiento")
public class IncidentsController {
    private static final String ADMINISTRATOR = "ROLE_ADMINISTRATOR";

    private final IncidentCommandService commandService;
    private final IncidentQueryService queryService;
    private static final long MAX_PHOTO_BYTES = 2L * 1024 * 1024;
    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png");

    private final AppointmentQueryService appointmentQueryService;
    private final IncidentPhotoStorage photos;

    @Autowired
    public IncidentsController(IncidentCommandService commandService, IncidentQueryService queryService,
                               AppointmentQueryService appointmentQueryService, IncidentPhotoStorage photos) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.appointmentQueryService = appointmentQueryService;
        this.photos = photos;
    }

    /** Without photo storage: uploads answer 503 and the form hides the photo field. */
    public IncidentsController(IncidentCommandService commandService, IncidentQueryService queryService,
                               AppointmentQueryService appointmentQueryService) {
        this(commandService, queryService, appointmentQueryService, new IncidentPhotoStorage(null, false));
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una incidencia de un dispositivo del hogar",
            description = "Solo la cuenta con la suscripción activa del hogar y la instalación aprobada. Nace OPEN.")
    public IncidentResource create(@Valid @RequestBody CreateIncidentResource resource, Authentication authentication) {
        var incident = commandService.handle(new CreateIncidentCommand(userIdOf(authentication), resource.householdId(),
                resource.deviceId(), resource.description(), resource.severity()));
        return toResources(List.of(incident)).getFirst();
    }

    @PostMapping(value = "/reports", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reportar una falla de un dispositivo instalado en el hogar (web del cuidador)",
            description = "Para la cuenta con el servicio activo. Nace Registrada y reserva la visita elegida (día, "
                    + "franja y técnico libre); 409 slot_unavailable si el técnico ya no está libre. El técnico la "
                    + "evalúa y confirma la visita desde la app.")
    public IncidentResource report(@Valid @RequestBody ReportIncidentResource resource, Authentication authentication) {
        var incident = commandService.handle(new ReportIncidentCommand(userIdOf(authentication), resource.deviceMac(),
                resource.problemType(), resource.description(), resource.occurredAt(),
                new SlotId(resource.visitDate(), new TimeRange(resource.visitStartTime(), resource.visitEndTime())),
                resource.technicianId()));
        return toResources(List.of(incident)).getFirst();
    }

    @GetMapping("/devices")
    @Operation(summary = "Dispositivos instalados del hogar de la cuenta, para el formulario de reporte",
            description = "Responde 404 mientras la cuenta no tenga el servicio activo.")
    public ResponseEntity<ReportableHomeResource> devices(Authentication authentication) {
        return queryService.handle(new GetReportableDevicesQuery(userIdOf(authentication)))
                .map(home -> new ReportableHomeResource(home.draftId(), home.directions(), home.contactName(),
                        java.util.stream.IntStream.range(0, home.devices().size())
                                .mapToObj(index -> new ReportableDeviceResource((long) index + 1,
                                        home.devices().get(index).alias(), home.devices().get(index).macAddress(),
                                        home.devices().get(index).type().name()))
                                .toList(), photos.isEnabled()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/{incidentId}/evidences", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adjuntar una fotografía como evidencia",
            description = "Recibe la referencia segura (HTTPS) que devolvió el servicio de almacenamiento tras la carga.")
    public IncidentResource attachEvidence(@PathVariable Long incidentId,
                                           @Valid @RequestBody AttachEvidenceResource resource,
                                           Authentication authentication) {
        var incident = commandService.handle(new AttachEvidenceCommand(incidentId, userIdOf(authentication),
                resource.url(), resource.publicId()));
        return toResources(List.of(incident)).getFirst();
    }

    @PostMapping(value = "/{incidentId}/evidences/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subir una foto de la incidencia (JPG o PNG, hasta 2 MB)",
            description = "Solo la familia que la reportó. Responde 503 photo_upload_unavailable si el almacenamiento "
                    + "de fotos no está configurado; la incidencia sigue registrada.")
    public IncidentResource uploadPhoto(@PathVariable Long incidentId, @RequestParam("file") MultipartFile file,
                                        Authentication authentication) {
        var userId = userIdOf(authentication);
        var incident = queryService.handle(new GetIncidentByIdQuery(incidentId))
                .orElseThrow(() -> new IncidentNotFound(incidentId));
        if (!incident.isReportedBy(userId)) throw new IncidentAccessDenied();
        if (file.isEmpty() || !PHOTO_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("The photo must be a JPG or PNG image.");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) throw new IllegalArgumentException("The photo can be up to 2 MB.");
        if (!photos.isEnabled()) throw new PhotoUploadUnavailable();
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("The photo could not be read.");
        }
        var stored = photos.store(data, file.getOriginalFilename());
        var updated = commandService.handle(new AttachEvidenceCommand(incidentId, userId, stored.url(), stored.publicId()));
        return toResources(List.of(updated)).getFirst();
    }

    @GetMapping
    @Operation(summary = "Listar las incidencias propias (el administrador ve todas)")
    public List<IncidentResource> list(@RequestParam(name = "status", required = false) IncidentStatus status,
                                       Authentication authentication) {
        var reportedBy = isAdministrator(authentication) ? null : userIdOf(authentication);
        return toResources(queryService.handle(new GetIncidentsQuery(reportedBy, status)));
    }

    @GetMapping("/{incidentId}")
    @Operation(summary = "Consultar una incidencia para darle seguimiento",
            description = "Incluye sus evidencias, el historial de estados con fecha (OPEN, SCHEDULED, IN_PROGRESS, "
                    + "RESOLVED) y las visitas de mantenimiento con su técnico.")
    public IncidentResource get(@PathVariable Long incidentId, Authentication authentication) {
        var incident = queryService.handle(new GetIncidentByIdQuery(incidentId))
                .filter(found -> isAdministrator(authentication) || found.isReportedBy(userIdOf(authentication)))
                .orElseThrow(() -> new IncidentNotFound(incidentId));
        return toResources(List.of(incident)).getFirst();
    }

    /** Builds the resources with the maintenance visits of every incident in one query. */
    private List<IncidentResource> toResources(List<Incident> incidents) {
        if (incidents.isEmpty()) return List.of();
        Map<Long, List<Appointment>> visits = appointmentQueryService
                .handle(new GetMaintenanceOrdersByIncidentIdsQuery(incidents.stream().map(Incident::getId).toList()))
                .stream()
                .collect(Collectors.groupingBy(Appointment::getIncidentId));
        return incidents.stream()
                .map(incident -> IncidentResourceFromEntityAssembler.toResourceFromEntity(incident,
                        visits.getOrDefault(incident.getId(), List.of())))
                .toList();
    }

    static Long userIdOf(Authentication authentication) {
        if (authentication == null) throw new IllegalArgumentException("Authenticated user is required.");
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Authenticated user identifier is invalid.", exception);
        }
    }

    static boolean isAdministrator(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMINISTRATOR.equals(authority.getAuthority()));
    }
}
