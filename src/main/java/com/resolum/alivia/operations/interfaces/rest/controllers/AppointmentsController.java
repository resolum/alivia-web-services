package com.resolum.alivia.operations.interfaces.rest.controllers;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.aggregates.EvaluationSlotHold;
import com.resolum.alivia.operations.domain.model.commands.HoldEvaluationSlotCommand;
import com.resolum.alivia.operations.domain.model.commands.ReleaseEvaluationSlotCommand;
import com.resolum.alivia.operations.domain.model.commands.ScheduleAppointmentCommand;
import com.resolum.alivia.operations.domain.model.queries.GetAvailableSlotsQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableSlot;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.domain.services.EvaluationSlotHoldCommandService;
import com.resolum.alivia.operations.interfaces.rest.resources.AppointmentResource;
import com.resolum.alivia.operations.interfaces.rest.resources.AvailableSlotResource;
import com.resolum.alivia.operations.interfaces.rest.resources.EvaluationSlotHoldResource;
import com.resolum.alivia.operations.interfaces.rest.resources.HoldEvaluationSlotResource;
import com.resolum.alivia.operations.interfaces.rest.resources.ScheduleAppointmentResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
    private final EvaluationSlotHoldCommandService holdService;

    public AppointmentsController(AppointmentCommandService commandService, AppointmentQueryService queryService,
                                 EvaluationSlotHoldCommandService holdService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.holdService = holdService;
    }

    @GetMapping("/available-slots")
    @Operation(summary = "Franjas disponibles según los horarios de los técnicos",
            description = "Ventanas de dos horas desde mañana y hasta siete días, una por cada técnico libre. "
                    + "Las franjas que otra solicitud tiene separadas no aparecen; la de draftId sí.")
    public List<AvailableSlotResource> availableSlots(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "days", defaultValue = "7") int days,
            @RequestParam(name = "draftId", required = false) String draftId) {
        return queryService.handle(new GetAvailableSlotsQuery(from, days, draftId)).stream()
                .map(AppointmentsController::toResource).toList();
    }

    @PutMapping(value = "/holds/{draftId}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Separar la franja y el técnico elegidos mientras se completa el pago",
            description = "Dura 30 minutos y se renueva al volver a llamarla. Una solicitud separa una sola franja.")
    public EvaluationSlotHoldResource hold(@PathVariable String draftId,
                                           @Valid @RequestBody HoldEvaluationSlotResource resource) {
        return toResource(holdService.handle(new HoldEvaluationSlotCommand(draftId, resource.date(),
                resource.startTime(), resource.endTime(), resource.technicianId())));
    }

    @DeleteMapping("/holds/{draftId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Liberar la franja separada para que otras familias la vean")
    public void release(@PathVariable String draftId) {
        holdService.handle(new ReleaseEvaluationSlotCommand(draftId));
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

    private static EvaluationSlotHoldResource toResource(EvaluationSlotHold hold) {
        return new EvaluationSlotHoldResource(hold.getDraftId(), hold.getScheduledDate(), hold.getStartTime().format(TIME),
                hold.getEndTime().format(TIME), hold.getTechnicianId(), hold.getTechnicianName(), hold.getExpiresAt());
    }

    private static AppointmentResource toResource(Appointment appointment) {
        return new AppointmentResource(appointment.getId(), appointment.getDraftId(), appointment.getScheduledDate(),
                appointment.getStartTime().format(TIME), appointment.getEndTime().format(TIME),
                appointment.getTechnicianId(), appointment.getTechnicianName(), appointment.getContactName(),
                appointment.getContactPhone(), appointment.getDirections(), appointment.getStatus().name());
    }
}
