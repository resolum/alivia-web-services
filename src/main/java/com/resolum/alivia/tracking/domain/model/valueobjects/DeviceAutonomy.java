package com.resolum.alivia.tracking.domain.model.valueobjects;

/**
 * The commands of one device.
 *
 * @param deviceId the device; {@code null} for the commands that name none (a rejection can happen before the device is known)
 */
public record DeviceAutonomy(Long deviceId, AutonomyCounts counts) {
}
