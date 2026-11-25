package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.time.Instant;
import java.util.List;

/**
 * What the family sees of one device: what it is, and the last reliable state reported for it.
 *
 * <p>A device that has never reported has {@code connectionStatus UNKNOWN} and no other state. A device whose edge is
 * offline keeps its last confirmed state and is marked {@code stale}: it is not shown as disconnected by inference,
 * because the lost link may be the edge's and not the device's.</p>
 *
 * @param deviceId            the device
 * @param alias               the name the business gave it
 * @param type                {@code MICROPHONE}, {@code DOOR}, {@code WINDOW}, {@code CURTAIN} or {@code LIGHT}
 * @param roomKey             the room where it is placed
 * @param connectionStatus    whether its edge reaches it; {@code UNKNOWN} when it has never reported
 * @param operationalStatus   how it works; {@code null} when it has never reported
 * @param currentState        what it is doing, if it says
 * @param batteryPct          the battery left, if it measures it
 * @param lastCommunicationAt the last time its edge heard from it
 * @param observedAt          when its edge observed this state
 * @param lastUpdatedAt       when the backend kept this state; {@code null} when it has never reported
 * @param faults              the codes of the failures it reports
 * @param stale               whether the state is the last known one because its edge is not heard now
 */
public record DeviceStatusView(Long deviceId, String alias, String type, String roomKey,
                               ConnectionStatus connectionStatus, OperationalStatus operationalStatus,
                               String currentState, Integer batteryPct, Instant lastCommunicationAt, Instant observedAt,
                               Instant lastUpdatedAt, List<String> faults, boolean stale) {

    public DeviceStatusView {
        faults = faults == null ? List.of() : List.copyOf(faults);
    }
}
