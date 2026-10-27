package com.resolum.alivia.subscriptions.domain.model.queries;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorQuantities;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;

public record QuotePlanQuery(PlanCode code, Integer rooms, ActuatorQuantities quantities) {
}
