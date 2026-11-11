package com.resolum.alivia.assistance.domain.model.queries;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.LocalDateTime;

public record GetShiftsQuery(
        Long assistedPersonId,
        UserId userId,
        LocalDateTime from,
        LocalDateTime to
) {
    public GetShiftsQuery(Long assistedPersonId, LocalDateTime from, LocalDateTime to) {
        this(assistedPersonId, null, from, to);
    }
}
