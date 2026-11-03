package com.resolum.alivia.subscriptions.interfaces.rest.transform;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.ContractingTracking;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.TechnicalVisit;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.ContractingTrackingResource;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.TechnicalVisitResource;

import java.time.format.DateTimeFormatter;

public final class ContractingTrackingResourceFromValueAssembler {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private ContractingTrackingResourceFromValueAssembler() {
    }

    public static ContractingTrackingResource toResource(ContractingTracking tracking, String code) {
        return new ContractingTrackingResource(tracking.draftId(), code, tracking.stage().name(),
                tracking.planCode().name(), tracking.planName(), tracking.rooms(), tracking.devices(),
                tracking.monthlyAmount(), tracking.currency(), toResource(tracking.evaluation()),
                toResource(tracking.installation()), tracking.confirmedAt());
    }

    private static TechnicalVisitResource toResource(TechnicalVisit visit) {
        return visit == null ? null : new TechnicalVisitResource(visit.date(), visit.startTime().format(TIME),
                visit.endTime().format(TIME), visit.technicianName(), visit.directions(),
                visit.completed() ? "COMPLETED" : "SCHEDULED", visit.result(), visit.notes());
    }
}
