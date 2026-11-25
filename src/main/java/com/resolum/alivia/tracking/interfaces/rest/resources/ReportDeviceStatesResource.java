package com.resolum.alivia.tracking.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * A report of the edge: how its devices are and that it is alive. Without devices it is only the heartbeat.
 *
 * @param reportedAt when the report was put together
 * @param edge       what the edge says about itself
 * @param devices    the state of each device
 */
public record ReportDeviceStatesResource(Instant reportedAt, EdgeHeartbeatResource edge,
                                         List<@NotNull DeviceStateResource> devices) {
}
