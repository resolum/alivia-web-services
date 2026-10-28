package com.resolum.alivia.support.domain.services;

import com.resolum.alivia.support.domain.model.aggregates.Appointment;
import com.resolum.alivia.support.domain.model.commands.ScheduleAppointmentCommand;

public interface AppointmentCommandService {
    Appointment handle(ScheduleAppointmentCommand command);
}
