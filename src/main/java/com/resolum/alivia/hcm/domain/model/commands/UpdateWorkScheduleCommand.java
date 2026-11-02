package com.resolum.alivia.hcm.domain.model.commands;

import com.resolum.alivia.hcm.domain.model.valueobjects.WorkShift;

import java.time.LocalDate;
import java.util.List;

/** Modifies a work schedule; a draft is published when {@code draft} is false. */
public record UpdateWorkScheduleCommand(Long employeeId, Long workScheduleId, Long businessId, String name,
        LocalDate startDate, LocalDate endDate, List<WorkShift> shifts, boolean draft, Long actorUserId) {
}
