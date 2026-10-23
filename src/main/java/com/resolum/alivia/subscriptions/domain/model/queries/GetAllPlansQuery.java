package com.resolum.alivia.subscriptions.domain.model.queries;

import com.resolum.alivia.subscriptions.domain.exceptions.InvalidPlan;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanStatus;

public record GetAllPlansQuery(PlanStatus status) {
    public GetAllPlansQuery {
        if (status != PlanStatus.ACTIVE) {
            throw new InvalidPlan("Solo está disponible la consulta de planes activos.");
        }
    }
}
