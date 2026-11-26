package com.resolum.alivia.tracking.domain.model.valueobjects;

/**
 * What became of the report of one device.
 *
 * @param deviceId the device
 * @param status   what happened to the report
 * @param reason   why it was rejected; {@code null} otherwise
 */
public record DeviceStateOutcome(Long deviceId, StateOutcome status, RejectionReason reason) {
}
