package com.resolum.alivia.tracking.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * One device of the home and its last known state. A device that has never reported has {@code connectionStatus UNKNOWN}
 * and no other state.
 *
 * @param stale whether the state is the last known one because the edge of the device is not heard now
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeDeviceStatusResource(Long deviceId, String alias, String type, String roomKey,
                                       String connectionStatus, String operationalStatus, String currentState,
                                       Integer batteryPct, Instant lastCommunicationAt, Instant observedAt,
                                       Instant lastUpdatedAt, List<String> faults, boolean stale) {
}
