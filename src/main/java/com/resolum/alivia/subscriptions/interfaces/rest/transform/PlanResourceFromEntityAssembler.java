package com.resolum.alivia.subscriptions.interfaces.rest.transform;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Plan;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.PlanResource;

public final class PlanResourceFromEntityAssembler {
    private PlanResourceFromEntityAssembler() {
    }

    public static PlanResource toResourceFromEntity(Plan plan) {
        return new PlanResource(
                plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(),
                plan.getStatus(), plan.getBasePrice().currency(), "MONTHLY",
                plan.getBasePrice().amount(), plan.minimumMonthlyPrice().amount(),
                plan.getPriceQualifier(),
                plan.getAdditionalRoomPrice() == null ? null : plan.getAdditionalRoomPrice().amount(),
                plan.getMinimumRooms(), plan.getMinActuators(), plan.getMaxActuators(),
                plan.getIncludedActuators(), plan.getDeviceMonthlyPrices(),
                plan.getFeatures(), plan.getIncludedDevices());
    }
}
