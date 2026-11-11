package com.resolum.alivia.assistance.domain.model.commands;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.LocalDateTime;

public record AssignShiftCommand(
        Long shiftId,
        Long assistedPersonId,
        UserId userId,
        Long caregiverId,
        String caregiverName,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String notes,
        boolean allowConflict
) {
    public AssignShiftCommand(Long shiftId,
                              Long assistedPersonId,
                              Long caregiverId,
                              String caregiverName,
                              LocalDateTime startsAt,
                              LocalDateTime endsAt,
                              String notes,
                              boolean allowConflict) {
        this(shiftId, assistedPersonId, null, caregiverId, caregiverName, startsAt, endsAt, notes, allowConflict);
    }
}
