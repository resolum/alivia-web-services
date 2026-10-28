package com.resolum.alivia.support.interfaces.rest.controllers;

import com.resolum.alivia.support.domain.model.aggregates.Appointment;
import com.resolum.alivia.support.domain.model.commands.ScheduleAppointmentCommand;
import com.resolum.alivia.support.domain.model.queries.GetAvailableSlotsQuery;
import com.resolum.alivia.support.domain.model.valueobjects.AvailableSlot;
import com.resolum.alivia.support.domain.services.AppointmentCommandService;
import com.resolum.alivia.support.domain.services.AppointmentQueryService;
import com.resolum.alivia.support.interfaces.rest.resources.AppointmentResource;
import com.resolum.alivia.support.interfaces.rest.resources.AvailableSlotResource;
import com.resolum.alivia.support.interfaces.rest.resources.ScheduleAppointmentResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/appointments", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Evaluación técnica", description = "Cita de evaluación presencial que agenda el familiar al contratar")
public class AppointmentsController {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentCommandService commandService;
    private final AppointmentQueryService queryService;

    public AppointmentsController(AppointmentCommandService commandService, AppointmentQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping("/available-slots")
    @Operation(summary = "Franjas disponibles según los horarios de los técnicos",
            description = "Ventanas de dos horas desde mañana y hasta siete días, con el técnico que atendería cada una.")
    public List<AvailableSlotResource> availableSlots(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "days", defaultValue = "7") int days) {
        return queryService.handle(new GetAvailableSlotsQuery(from, days)).stream().map(AppointmentsController::toResource).toList();
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agendar la evaluación técnica con el técnico de la franja elegida")
    public AppointmentResource schedule(@Valid @RequestBody ScheduleAppointmentResource resource) {
        return toResource(commandService.handle(new ScheduleAppointmentCommand(resource.draftId(), resource.date(),
                resource.startTime(), resource.endTime(), resource.technicianId(), resource.contactName(),
                resource.contactPhone(), resource.directions())));
    }

    private static AvailableSlotResource toResource(AvailableSlot slot) {
        return new AvailableSlotResource(slot.date(), slot.window().start().format(TIME), slot.window().end().format(TIME),
                slot.technicianId(), slot.technicianName(), slot.availableTechnicians());
    }

    private static AppointmentResource toResource(Appointment appointment) {
        return new AppointmentResource(appointment.getId(), appointment.getDraftId(), appointment.getScheduledDate(),
                appointment.getStartTime().format(TIME), appointment.getEndTime().format(TIME),
                appointment.getTechnicianId(), appointment.getTechnicianName(), appointment.getContactName(),
                appointment.getContactPhone(), appointment.getDirections(), appointment.getStatus().name());
    }
}
