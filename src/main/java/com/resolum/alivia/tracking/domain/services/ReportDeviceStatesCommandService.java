package com.resolum.alivia.tracking.domain.services;

import com.resolum.alivia.tracking.domain.model.commands.ReportDeviceStatesCommand;
import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceStateOutcome;

import java.util.List;

/** Keeps the last reliable state of the devices of an edge and records that the edge is alive. */
public interface ReportDeviceStatesCommandService {

    /**
     * Records the heartbeat of the edge and applies the state of each device, in order, answering what became of each.
     * A report observed before the state already kept is {@code STALE} and an invalid one {@code REJECTED}: both leave
     * the kept state as it is. A change of state adds its events to the history (a device that disconnected, failed,
     * recovered or changed what it does).
     *
     * @throws com.resolum.alivia.tracking.domain.exceptions.BatchTooLarge when it reports more devices than allowed
     * @throws com.resolum.alivia.tracking.domain.exceptions.EdgeNotInInstallation when the edge has no active home
     * @throws com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable when it cannot be kept; the edge
     *         sends the same report again
     */
    List<DeviceStateOutcome> handle(ReportDeviceStatesCommand command);
}
