package com.resolum.alivia.assets.interfaces.rest.resources;

import java.util.List;

/**
 * The table of devices of a household, as an edge service stores it. It is always the complete table; the edge keeps
 * the one with the highest {@code version} and ignores any older one.
 *
 * @param nodes devices of the household, ordered by device id
 */
public record EdgeDeviceTableResource(Long installationId, Long householdId, long version, List<Node> nodes) {

    public record Node(Long deviceId, String macAddress, String deviceType, String status, String roomKey,
                       String function) {
    }
}
