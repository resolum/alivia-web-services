package com.resolum.alivia.tracking.interfaces.rest.transform;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceEventsPageView;
import com.resolum.alivia.tracking.interfaces.rest.resources.DeviceEventResource;
import com.resolum.alivia.tracking.interfaces.rest.resources.DeviceEventsPageResource;

/** From a page of the history to what the API shows. */
public final class DeviceEventsPageResourceAssembler {

    private DeviceEventsPageResourceAssembler() {
    }

    public static DeviceEventsPageResource toResource(DeviceEventsPageView view) {
        return new DeviceEventsPageResource(view.deviceId(), view.from(), view.to(), view.page(), view.size(),
                view.total(), view.totalPages(), view.items().stream().map(DeviceEventsPageResourceAssembler::toResource).toList());
    }

    private static DeviceEventResource toResource(DeviceEvent event) {
        var attributes = event.attributes() == null || event.attributes().isEmpty() ? null : event.attributes().values();
        return new DeviceEventResource(event.eventId(), event.type().name(), event.severity().name(),
                event.result() == null ? null : event.result().name(), event.origin().name(), event.occurredAt(),
                event.receivedAt(), event.commandId(), event.action() == null ? null : event.action().name(),
                event.confidence(), event.batteryPct(), event.detail(), attributes);
    }
}
