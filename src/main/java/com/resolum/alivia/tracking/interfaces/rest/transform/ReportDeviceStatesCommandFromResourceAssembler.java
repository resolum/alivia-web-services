package com.resolum.alivia.tracking.interfaces.rest.transform;

import com.resolum.alivia.tracking.domain.model.commands.ReportDeviceStatesCommand;
import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceStateOutcome;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedDeviceState;
import com.resolum.alivia.tracking.interfaces.rest.resources.DeviceStateOutcomeResource;
import com.resolum.alivia.tracking.interfaces.rest.resources.DeviceStatesOutcomeResource;
import com.resolum.alivia.tracking.interfaces.rest.resources.ReportDeviceStatesResource;

import java.util.List;

/** Between the report of device states of the edge contract and the domain. */
public final class ReportDeviceStatesCommandFromResourceAssembler {

    private ReportDeviceStatesCommandFromResourceAssembler() {
    }

    public static ReportDeviceStatesCommand toCommand(Long edgeId, ReportDeviceStatesResource resource) {
        var pending = resource.edge() == null || resource.edge().pendingEvents() == null
                ? 0 : resource.edge().pendingEvents();
        var devices = resource.devices() == null ? List.<ReportedDeviceState>of() : resource.devices().stream()
                .map(device -> new ReportedDeviceState(device.deviceId(), device.connectionStatus(),
                        device.operationalStatus(), device.currentState(), device.batteryPct(),
                        device.lastCommunicationAt(), device.observedAt(), device.faults(), device.diagnostics()))
                .toList();
        return new ReportDeviceStatesCommand(edgeId, pending, devices);
    }

    public static DeviceStatesOutcomeResource toResource(List<DeviceStateOutcome> outcomes) {
        return new DeviceStatesOutcomeResource(outcomes.stream()
                .map(outcome -> new DeviceStateOutcomeResource(outcome.deviceId(), outcome.status().name(),
                        outcome.reason() == null ? null : outcome.reason().code()))
                .toList());
    }
}
