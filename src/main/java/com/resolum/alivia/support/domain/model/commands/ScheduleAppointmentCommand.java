package com.resolum.alivia.support.domain.model.commands;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleAppointmentCommand(String draftId, LocalDate date, LocalTime startTime, LocalTime endTime,
                                         Long technicianId, String contactName, String contactPhone,
                                         String directions) {
}
