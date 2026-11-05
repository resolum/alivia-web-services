package com.resolum.alivia.assets.domain.model.events;

import com.resolum.alivia.assets.domain.model.aggregates.Asset;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;

/**
 * A device or edge changed status on its own (maintenance, broken, retired...). Assigning or unassigning a
 * device also changes its status, but that is announced by {@link DeviceAssignedEvent} and
 * {@link DeviceUnassignedEvent}, which carry the placement, so it is not announced twice.
 *
 * <p>The asset is carried by reference: its id exists once the change is saved, which is when the event is
 * published.</p>
 */
public record AssetStatusChangedEvent(Asset asset, AssetStatus previousStatus, AssetStatus newStatus) {
}
