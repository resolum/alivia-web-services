package com.resolum.alivia.operations.interfaces.rest.controllers;

import com.resolum.alivia.operations.domain.exceptions.IncidentNotFound;
import com.resolum.alivia.operations.domain.model.commands.AttachEvidenceCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateIncidentCommand;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentsQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatus;
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

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/support-incidents", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Soporte técnico", description = "Incidencias de los hogares en servicio y sus visitas de mantenimiento")
public class IncidentsController {
    private static final String ADMINISTRATOR = "ROLE_ADMINISTRATOR";

    private final IncidentCommandService commandService;
    private final IncidentQueryService queryService;

    public IncidentsController(IncidentCommandService commandService, IncidentQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una incidencia de un dispositivo del hogar",
            description = "Solo la cuenta con la suscripción activa del hogar y la instalación aprobada. Nace OPEN.")
    public IncidentResource create(@Valid @RequestBody CreateIncidentResource resource, Authentication authentication) {
        var incident = commandService.handle(new CreateIncidentCommand(userIdOf(authentication), resource.householdId(),
                resource.deviceId(), resource.description(), resource.severity()));
        return IncidentResourceFromEntityAssembler.toResourceFromEntity(incident);
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
        return IncidentResourceFromEntityAssembler.toResourceFromEntity(incident);
    }

    @GetMapping
    @Operation(summary = "Listar las incidencias propias (el administrador ve todas)")
    public List<IncidentResource> list(@RequestParam(name = "status", required = false) IncidentStatus status,
                                       Authentication authentication) {
        var reportedBy = isAdministrator(authentication) ? null : userIdOf(authentication);
        return queryService.handle(new GetIncidentsQuery(reportedBy, status)).stream()
                .map(IncidentResourceFromEntityAssembler::toResourceFromEntity).toList();
    }

    @GetMapping("/{incidentId}")
    @Operation(summary = "Consultar una incidencia con sus evidencias",
            description = "Incluye el historial de estados con la fecha de cada paso.")
    public IncidentResource get(@PathVariable Long incidentId, Authentication authentication) {
        return queryService.handle(new GetIncidentByIdQuery(incidentId))
                .filter(incident -> isAdministrator(authentication) || incident.isReportedBy(userIdOf(authentication)))
                .map(IncidentResourceFromEntityAssembler::toResourceFromEntity)
                .orElseThrow(() -> new IncidentNotFound(incidentId));
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
