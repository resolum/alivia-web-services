package com.resolum.alivia.tracking.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The state of one device as its edge reports it.
 *
 * @param deviceId            the device, from the table of the installation
 * @param connectionStatus    {@code CONNECTED}, {@code DISCONNECTED} or {@code UNKNOWN}
 * @param operationalStatus   {@code OK}, {@code DEGRADED} or {@code FAILED}
 * @param currentState        what it is doing ({@code open}, {@code closed}, {@code on}, {@code off}…)
 * @param batteryPct          the battery left, 0 to 100, if the device measures it
 * @param lastCommunicationAt the last time the edge heard from the device
 * @param observedAt          when the edge observed this state
 * @param faults              the codes of the failures it reports
 * @param diagnostics         readings such as signal or free memory, kept only from time to time
 */
public record DeviceStateResource(Long deviceId, String connectionStatus, String operationalStatus, String currentState,
                                  Integer batteryPct, Instant lastCommunicationAt, Instant observedAt,
                                  List<String> faults, Map<String, Object> diagnostics) {
}
