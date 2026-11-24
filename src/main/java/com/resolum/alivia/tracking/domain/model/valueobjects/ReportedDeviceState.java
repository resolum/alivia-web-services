package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The state of a device as an edge reports it. */
public record ReportedDeviceState(Long deviceId, String connectionStatus, String operationalStatus, String currentState,
                                  Integer batteryPct, Instant lastCommunicationAt, Instant observedAt,
                                  List<String> faults, Map<String, Object> diagnostics) {
}
