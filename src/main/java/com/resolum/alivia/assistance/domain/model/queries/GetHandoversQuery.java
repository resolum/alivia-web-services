package com.resolum.alivia.assistance.domain.model.queries;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

public record GetHandoversQuery(
        Long assistedPersonId,
        UserId userId
) {
    public GetHandoversQuery(Long assistedPersonId) {
        this(assistedPersonId, null);
    }
}
