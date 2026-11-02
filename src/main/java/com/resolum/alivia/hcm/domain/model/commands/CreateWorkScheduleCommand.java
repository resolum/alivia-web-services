package com.resolum.alivia.hcm.domain.model.commands;

import com.resolum.alivia.hcm.domain.model.valueobjects.WorkShift;

import java.time.LocalDate;
import java.util.List;

/** Registers the work schedule of a contract; {@code draft} keeps it out of effect until it is published. */
public record CreateWorkScheduleCommand(Long employeeId, Long contractId, Long businessId, String name,
        LocalDate startDate, LocalDate endDate, List<WorkShift> shifts, boolean draft, Long actorUserId) {
}
