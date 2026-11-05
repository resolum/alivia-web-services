package com.resolum.alivia.operations.domain.services;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.commands.AssignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.commands.CompleteMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.StartMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.BookIncidentVisitCommand;
import com.resolum.alivia.operations.domain.model.commands.ConfirmIncidentVisitCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.EvaluateIncidentCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateTechnicalOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.ReassignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.commands.OpenInstallationCommand;
import com.resolum.alivia.operations.domain.model.commands.RecordEvaluationResultCommand;
import com.resolum.alivia.operations.domain.model.commands.RecordInstallationResultCommand;
import com.resolum.alivia.operations.domain.model.commands.ScheduleAppointmentCommand;
import com.resolum.alivia.operations.domain.model.commands.RegisterInstalledDeviceCommand;
import com.resolum.alivia.operations.domain.model.commands.RemoveInstalledDeviceCommand;
import com.resolum.alivia.operations.domain.model.commands.StartTechnicalOrderCommand;

import java.util.Optional;

public interface AppointmentCommandService {
    Appointment handle(ScheduleAppointmentCommand command);

    Appointment handle(CreateTechnicalOrderCommand command);

    Appointment handle(AssignTechnicianCommand command);

    Appointment handle(ReassignTechnicianCommand command);

    Appointment handle(RecordEvaluationResultCommand command);

    /** The installation order of the request, opened now or earlier; empty while there is no viable evaluation. */
    Optional<Appointment> handle(OpenInstallationCommand command);

    Appointment handle(RecordInstallationResultCommand command);

    Appointment handle(CreateMaintenanceOrderCommand command);

    /** Reserves the visit chosen in the report; the incident stays registered until the technician evaluates it. */
    Appointment handle(BookIncidentVisitCommand command);

    /** Registrada → En evaluación, by the technician of the order. */
    Appointment handle(EvaluateIncidentCommand command);

    /** En evaluación → Visita programada, by the technician of the order. */
    Appointment handle(ConfirmIncidentVisitCommand command);

    Appointment handle(StartMaintenanceOrderCommand command);

    Appointment handle(CompleteMaintenanceOrderCommand command);

    Appointment handle(StartTechnicalOrderCommand command);

    Appointment handle(RegisterInstalledDeviceCommand command);

    Appointment handle(RemoveInstalledDeviceCommand command);
}
