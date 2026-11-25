package com.resolum.alivia.tracking.interfaces.rest.transform;

import com.resolum.alivia.tracking.domain.model.valueobjects.EventOutcome;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.interfaces.rest.resources.BatchEventResource;
import com.resolum.alivia.tracking.interfaces.rest.resources.EventBatchOutcomeResource;
import com.resolum.alivia.tracking.interfaces.rest.resources.EventOutcomeResource;

import java.util.List;

/** Between the batch of the edge contract and the domain. */
public final class ReportedEventFromBatchResourceAssembler {

    private ReportedEventFromBatchResourceAssembler() {
    }

    public static ReportedEvent toReportedEvent(BatchEventResource resource) {
        return new ReportedEvent(resource.eventId(), resource.type(), resource.occurredAt(), resource.deviceId(),
                resource.commandId(), resource.action(), resource.result(), resource.confidence(),
                resource.batteryPct(), resource.detail(), resource.attributes());
    }

    public static EventBatchOutcomeResource toResource(List<EventOutcome> outcomes) {
        return new EventBatchOutcomeResource(outcomes.stream()
                .map(outcome -> new EventOutcomeResource(outcome.eventId(), outcome.status().name(),
                        outcome.reason() == null ? null : outcome.reason().code()))
                .toList());
    }
}
