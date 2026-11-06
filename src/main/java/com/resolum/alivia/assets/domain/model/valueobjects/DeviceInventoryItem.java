package com.resolum.alivia.assets.domain.model.valueobjects;

import com.resolum.alivia.assets.domain.model.aggregates.Device;

/**
 * A device of the inventory as the business reads it.
 *
 * @param installationId installation where the device is assigned now, or {@code null} when it is not assigned
 */
public record DeviceInventoryItem(Device device, Long installationId) {
}
