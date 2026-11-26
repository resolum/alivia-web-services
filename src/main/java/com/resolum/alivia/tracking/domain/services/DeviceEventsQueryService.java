package com.resolum.alivia.tracking.domain.services;

import com.resolum.alivia.tracking.domain.model.queries.GetDeviceEventsQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceEventsPageView;

/** Answers the history of events of a device. */
public interface DeviceEventsQueryService {

    /**
     * The events of the device in the period, newest first, only the ones of the home where it is placed now: what
     * happened to it in a home it left is not shown to the new one.
     *
     * @throws com.resolum.alivia.tracking.domain.exceptions.InvalidHistoryQuery when the period, the page or the type is
     *         not valid
     * @throws com.resolum.alivia.tracking.domain.exceptions.DeviceNotAccessible when the device does not exist, is not
     *         placed in a home, or the user may not read it
     * @throws com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable when the events cannot be read
     */
    DeviceEventsPageView handle(GetDeviceEventsQuery query);
}
