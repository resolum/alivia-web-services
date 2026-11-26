package com.resolum.alivia.tracking.domain.model.commands;

import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedDeviceState;

import java.util.List;

/**
 * An edge reports how its devices are and that it is alive. A report without devices is just the heartbeat.
 *
 * @param edgeId        the edge that reports (from its credential)
 * @param pendingEvents how many events the edge still has to send; none when it does not say
 * @param devices       the state of each device
 */
public record ReportDeviceStatesCommand(Long edgeId, int pendingEvents, List<ReportedDeviceState> devices) {

    public ReportDeviceStatesCommand {
        if (edgeId == null) throw new IllegalArgumentException("The edge is required.");
        devices = devices == null ? List.of() : List.copyOf(devices);
    }
}
