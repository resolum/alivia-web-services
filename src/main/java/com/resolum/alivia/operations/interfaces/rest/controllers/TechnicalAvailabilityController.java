package com.resolum.alivia.operations.interfaces.rest.controllers;

import com.resolum.alivia.operations.domain.model.queries.GetTechnicalAvailabilityQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.interfaces.rest.resources.TechnicalAvailabilityResource;
import com.resolum.alivia.operations.interfaces.rest.transform.TechnicalOrderResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/technical-availability", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Órdenes técnicas", description = "Disponibilidad, órdenes de evaluación e instalación y asignación de técnicos")
public class TechnicalAvailabilityController {
    private final AppointmentQueryService queryService;

    public TechnicalAvailabilityController(AppointmentQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Bloques con capacidad técnica disponible",
            description = "Bloques de dos horas para evaluación (hasta 7 días) y de cuatro para instalación (hasta 14 días), "
                    + "con cuántas órdenes puede tomar cada uno, sin exponer la identidad de los técnicos.")
    public List<TechnicalAvailabilityResource> availability(
            @RequestParam(name = "serviceType") ServiceType serviceType,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "days", defaultValue = "1") int days) {
        return queryService.handle(new GetTechnicalAvailabilityQuery(serviceType, date, days)).stream()
                .map(TechnicalOrderResourceFromEntityAssembler::toResourceFromBlock).toList();
    }
}
