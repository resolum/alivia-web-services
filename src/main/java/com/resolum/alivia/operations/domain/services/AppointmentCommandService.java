package com.resolum.alivia.operations.domain.services;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.commands.ScheduleAppointmentCommand;

public interface AppointmentCommandService {
    Appointment handle(ScheduleAppointmentCommand command);
}
