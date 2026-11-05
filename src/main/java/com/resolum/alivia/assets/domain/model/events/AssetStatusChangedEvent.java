package com.resolum.alivia.assets.domain.model.events;

import com.resolum.alivia.assets.domain.model.valueobjects.AssetKind;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;

/**
 * A device or edge changed status on its own (maintenance, broken, retired...). Assigning or unassigning a device also
 * changes its status, but that is announced by {@link DeviceAssignedEvent} and {@link DeviceUnassignedEvent}, which
 * carry the placement, so it is not announced twice.
 *
 * @param assetKind      device or edge
 * @param assetId        id of the asset
 * @param macAddress     physical identifier (canonical form)
 * @param assetType      device type or edge type name
 * @param previousStatus status before the change
 * @param newStatus      status after the change
 */
public record AssetStatusChangedEvent(AssetKind assetKind, Long assetId, String macAddress, String assetType,
                                      AssetStatus previousStatus, AssetStatus newStatus) {
}
