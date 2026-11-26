package com.resolum.alivia.tracking.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What became of the report of one device.
 *
 * @param deviceId the device
 * @param status   {@code APPLIED}, {@code STALE} or {@code REJECTED}
 * @param reason   the code of the rejection; absent otherwise
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceStateOutcomeResource(Long deviceId, String status, String reason) {
}
