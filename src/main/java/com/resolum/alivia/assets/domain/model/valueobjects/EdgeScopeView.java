package com.resolum.alivia.assets.domain.model.valueobjects;

import java.util.List;

/**
 * What an edge service is in the installation where it is assigned: its kind, the installation and the household, and the
 * devices placed there. It is what lets another context tell that a report comes from that installation and is about one
 * of its devices.
 *
 * @param edgeId         the edge
 * @param edgeType       whether it is the microphone or the actuators edge of the installation
 * @param installationId the installation where it is assigned
 * @param householdId    the household of that installation
 * @param devices        the devices placed in the installation
 */
public record EdgeScopeView(Long edgeId, EdgeType edgeType, Long installationId, Long householdId,
                            List<ScopedDevice> devices) {

    public EdgeScopeView {
        devices = devices == null ? List.of() : List.copyOf(devices);
    }

    /** A device placed in the installation. */
    public record ScopedDevice(Long deviceId, String alias, DeviceType type, AssetStatus status, String roomKey) {
    }
}
