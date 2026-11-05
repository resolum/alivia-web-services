package com.resolum.alivia.subscriptions.interfaces.rest.controllers;

import com.resolum.alivia.subscriptions.domain.model.queries.GetServicedHomeByIdQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetServicedHomesQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ServicedHome;
import com.resolum.alivia.subscriptions.domain.services.ServicedHomeQueryService;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.ServicedHomeResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/serviced-homes", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Hogares", description = "Hogares en servicio: instalación aprobada, plan, dispositivos y órdenes técnicas")
public class ServicedHomesController {
    private static final String ADMINISTRATOR = "ROLE_ADMINISTRATOR";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ServicedHomeQueryService queryService;

    public ServicedHomesController(ServicedHomeQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Listar los hogares en servicio (instalación aprobada)")
    public List<ServicedHomeResource> list() {
        return queryService.handle(new GetServicedHomesQuery(null)).stream().map(ServicedHomesController::toResource).toList();
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Hogares en servicio que el técnico atendió o tiene asignados")
    public List<ServicedHomeResource> mine(Authentication authentication) {
        return queryService.handle(new GetServicedHomesQuery(userIdOf(authentication))).stream()
                .map(ServicedHomesController::toResource).toList();
    }

    @GetMapping("/{homeId}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TECHNICIAN')")
    @Operation(summary = "Consultar un hogar en servicio",
            description = "Un técnico solo consulta los hogares que atendió; los demás responden 404.")
    public ResponseEntity<ServicedHomeResource> get(@PathVariable String homeId, Authentication authentication) {
        var technician = isAdministrator(authentication) ? null : userIdOf(authentication);
        return queryService.handle(new GetServicedHomeByIdQuery(homeId, technician))
                .map(ServicedHomesController::toResource)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static boolean isAdministrator(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority -> ADMINISTRATOR.equals(authority.getAuthority()));
    }

    static Long userIdOf(Authentication authentication) {
        if (authentication == null) throw new IllegalArgumentException("Authenticated user is required.");
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Authenticated user identifier is invalid.", exception);
        }
    }

    static ServicedHomeResource toResource(ServicedHome home) {
        var request = home.request();
        return new ServicedHomeResource(home.draftId(), home.code(), home.contactName(), home.contactPhone(), home.directions(),
                home.installedAt(), home.installedBy(),
                request == null ? null : request.planCode().name(), request == null ? null : request.planName(),
                request == null ? null : request.billingCycle(), home.monthlyAmount(),
                request == null ? null : request.currency(),
                request == null || request.paymentStatus() == null ? null : request.paymentStatus().name(),
                request == null ? 0 : request.rooms(),
                home.devices().stream().map(device -> new ServicedHomeResource.Device(device.alias(), device.macAddress(), device.type())).toList(),
                home.orders().stream().map(order -> new ServicedHomeResource.Order(order.id(), order.code(), order.serviceType(),
                        order.status(), order.date(), order.startTime() == null ? null : order.startTime().format(TIME),
                        order.endTime() == null ? null : order.endTime().format(TIME), order.technicianName(),
                        order.startedAt(), order.completedAt(), order.incidentCode(), order.incidentStatus())).toList());
    }
}
