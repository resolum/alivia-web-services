package com.resolum.alivia.operations.domain.services;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.commands.AssignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.commands.CompleteMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateTechnicalOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.ReassignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.commands.RecordInstallationResultCommand;
import com.resolum.alivia.operations.domain.model.commands.ScheduleAppointmentCommand;

public interface AppointmentCommandService {
    Appointment handle(ScheduleAppointmentCommand command);

    Appointment handle(CreateTechnicalOrderCommand command);

    Appointment handle(AssignTechnicianCommand command);

    Appointment handle(ReassignTechnicianCommand command);

    Appointment handle(RecordInstallationResultCommand command);

    Appointment handle(CreateMaintenanceOrderCommand command);

    Appointment handle(CompleteMaintenanceOrderCommand command);
}
