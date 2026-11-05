package com.resolum.alivia.assets.domain.model.valueobjects;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;

import java.util.Comparator;
import java.util.List;

/**
 * The table of devices that a household keeps for its edge services: which devices exist in the installation, in
 * which room and with which function, so the edge can validate and run them locally (it stores the table in its own
 * database).
 *
 * <p>It is always a complete snapshot of the active assignments of the installation. The edge replaces its copy with
 * it, so receiving the same table twice, or out of date and then up to date, ends in the same state.</p>
 *
 * @param installationId installation the table belongs to
 * @param householdId    household of that installation
 * @param nodes          devices of the installation, ordered by device id
 */
public record EdgeAccessTable(Long installationId, Long householdId, List<NodeRef> nodes) {

    public EdgeAccessTable {
        nodes = List.copyOf(nodes);
    }

    /** Builds the table from the assignments that are still active; closed ones are ignored. */
    public static EdgeAccessTable of(Long installationId, Long householdId, List<DeviceAssignment> assignments) {
        var nodes = assignments.stream()
                .filter(DeviceAssignment::isActive)
                .map(assignment -> new NodeRef(assignment.getDevice().getId(),
                        assignment.getDevice().getMacAddress().macAddress(), assignment.getDevice().getDeviceType(),
                        assignment.getDevice().getAssetStatus(), assignment.getRoomKey(), assignment.getFunction()))
                .sorted(Comparator.comparing(NodeRef::deviceId))
                .toList();
        return new EdgeAccessTable(installationId, householdId, nodes);
    }
}
