package com.resolum.alivia.tracking.interfaces.rest.transform;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.RecentEventsView;
import com.resolum.alivia.tracking.interfaces.rest.resources.RecentEventResource;
import com.resolum.alivia.tracking.interfaces.rest.resources.RecentEventsResource;

/** From the recent events of a home to what the API shows. */
public final class RecentEventsResourceAssembler {

    private RecentEventsResourceAssembler() {
    }

    public static RecentEventsResource toResource(Long personId, RecentEventsView view) {
        return new RecentEventsResource(personId, view.limit(),
                view.items().stream().map(RecentEventsResourceAssembler::toResource).toList());
    }

    private static RecentEventResource toResource(DeviceEvent event) {
        var attributes = event.attributes() == null || event.attributes().isEmpty() ? null : event.attributes().values();
        return new RecentEventResource(event.eventId(), event.type().name(), event.severity().name(),
                event.result() == null ? null : event.result().name(), event.origin().name(), event.deviceId(),
                event.occurredAt(), event.receivedAt(), event.commandId(),
                event.action() == null ? null : event.action().name(), event.confidence(), event.batteryPct(),
                event.detail(), attributes);
    }
}
