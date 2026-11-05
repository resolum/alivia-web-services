package com.resolum.alivia.assets.domain.model.valueobjects;

/**
 * One entry of an {@link EdgeAccessTable}: a device of the household with what the edge services need to run it
 * locally.
 *
 * @param deviceId   id of the device in the inventory
 * @param macAddress physical identifier (canonical form)
 * @param deviceType kind of device
 * @param status     status of the device; the edge decides what to do with one in maintenance or broken
 * @param roomKey    room of the household where it is
 * @param function   what it does there
 */
public record NodeRef(Long deviceId, String macAddress, DeviceType deviceType, AssetStatus status, String roomKey,
                      String function) {
}
