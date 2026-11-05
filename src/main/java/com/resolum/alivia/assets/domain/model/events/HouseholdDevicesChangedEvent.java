package com.resolum.alivia.assets.domain.model.events;

import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;

/**
 * The table of devices of a household changed. It carries the complete table as it was right after the change, taken
 * inside the same locked section that gave it its version, so it is exactly the state that version stands for.
 *
 * @param cause          what changed
 * @param householdId    household whose table changed
 * @param installationId installation of that household
 * @param deviceId       device the change is about
 * @param table          the whole table after the change, with its version
 */
public record HouseholdDevicesChangedEvent(Cause cause, Long householdId, Long installationId, Long deviceId,
                                           EdgeAccessTable table) {

    public enum Cause {
        DEVICE_ASSIGNED,
        DEVICE_UNASSIGNED,
        /** A device of the household changed its status, its MAC address or its type. */
        DEVICE_CHANGED
    }
}
