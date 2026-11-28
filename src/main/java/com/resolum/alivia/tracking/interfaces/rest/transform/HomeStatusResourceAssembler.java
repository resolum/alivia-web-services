package com.resolum.alivia.tracking.interfaces.rest.transform;

import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceStatusView;
import com.resolum.alivia.tracking.domain.model.valueobjects.HomeStatusView;
import com.resolum.alivia.tracking.interfaces.rest.resources.HomeDeviceStatusResource;
import com.resolum.alivia.tracking.interfaces.rest.resources.HomeEdgeStatusResource;
import com.resolum.alivia.tracking.interfaces.rest.resources.HomeStatusResource;

/** From the state of a home to what the API shows. */
public final class HomeStatusResourceAssembler {

    private HomeStatusResourceAssembler() {
    }

    public static HomeStatusResource toResource(Long personId, HomeStatusView view) {
        return new HomeStatusResource(personId, view.householdId(), view.link().name(), view.offlineSince(),
                view.lastUpdatedAt(), view.mayHavePendingEvents(),
                view.devices().stream().map(HomeStatusResourceAssembler::toResource).toList(),
                view.edges().stream().map(edge -> new HomeEdgeStatusResource(edge.edgeId(), edge.kind(), edge.link(),
                        edge.lastReportAt(), edge.offlineSince())).toList());
    }

    private static HomeDeviceStatusResource toResource(DeviceStatusView device) {
        return new HomeDeviceStatusResource(device.deviceId(), device.alias(), device.type(), device.roomKey(),
                device.connectionStatus().name(),
                device.operationalStatus() == null ? null : device.operationalStatus().name(), device.currentState(),
                device.batteryPct(), device.lastCommunicationAt(), device.observedAt(), device.lastUpdatedAt(),
                device.faults(), device.stale());
    }
}
