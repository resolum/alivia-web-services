package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeDeviceTableResource;

public class EdgeDeviceTableResourceFromViewAssembler {
    public static EdgeDeviceTableResource toResourceFromView(EdgeAccessTable table) {
        var nodes = table.nodes().stream()
                .map(node -> new EdgeDeviceTableResource.Node(node.deviceId(), node.macAddress(),
                        node.deviceType().name(), node.status().name(), node.roomKey(), node.function()))
                .toList();
        return new EdgeDeviceTableResource(table.installationId(), table.householdId(), table.version(), nodes);
    }
}
