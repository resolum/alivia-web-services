package com.resolum.alivia.assets.domain.model.valueobjects;

/**
 * Where a device is, or was, placed and how it was at that moment. A plain copy of the facts, so whoever receives it
 * (an event, a message) does not depend on the entities and sees what happened, not what the data is later.
 *
 * @param installationId installation the device was placed in
 * @param householdId    household of that installation
 * @param deviceId       id of the device
 * @param macAddress     physical identifier (canonical form)
 * @param deviceType     kind of device
 * @param deviceStatus   status of the device right after the change
 * @param roomKey        room of the household where it is
 * @param function       what it does there
 */
public record DevicePlacement(Long installationId, Long householdId, Long deviceId, String macAddress,
                              DeviceType deviceType, AssetStatus deviceStatus, String roomKey, String function) {
}
